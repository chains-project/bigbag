package github.chains;

import spoon.Launcher;
import spoon.OutputType;
import spoon.compiler.Environment;
import spoon.experimental.CtUnresolvedImport;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic, reusable Spoon transformation rule for a Maven dependency
 * "package relocation" breaking change: a public dependency type that callers
 * referenced from source (plain imports, static contexts, annotation values
 * such as {@code @RunWith(Foo.class)}, {@code Foo.class} literals and simple
 * type usages) was moved from one package to another in a newer version of
 * the dependency. Every reference to {@code <oldPackage>.<TypeName>} must
 * become {@code <newPackage>.<TypeName>}; the type itself keeps its name,
 * constructors and methods, so no call-site restructuring is needed.
 *
 * <p>The rule matches the OLD API pattern structurally (any type reference /
 * import whose fully-qualified name lies directly under the dependency's old
 * package), traverses all files of the given source roots and applies the fix
 * wherever the pattern appears. It is parameterized only by the dependency's
 * fully-qualified package names plus the client's source directories; it
 * contains no client-project identifiers, so any Maven project affected by the
 * same breaking change is fixed by changing the input source directory path
 * (optionally the {@code --from}/{@code --to} package arguments).</p>
 *
 * <pre>
 * Usage:
 *   java github.chains.Main &lt;srcDir1&gt; [srcDir2 ...] [--from &lt;oldPackage&gt;] [--to &lt;newPackage&gt;]
 *
 * Example - fix any project broken by the relocated Mockito runner package
 * (Mockito 1.x/2.x "org.mockito.runners" removed in mockito-core 4.1.0):
 *   java github.chains.Main /path/to/project/src/test/java
 *
 * Example - generic relocation of a different dependency package:
 *   java github.chains.Main /path/to/project/src/main/java /path/to/project/src/test/java \
 *       --from com.old.library.pkg --to com.new.library.pkg
 * </pre>
 */
public final class Main {

    /** Package as published by the OLD dependency version (default instance of the breaking change). */
    private static final String DEFAULT_OLD_PACKAGE = "org.mockito.runners";
    /** Package as published by the NEW dependency version. */
    private static final String DEFAULT_NEW_PACKAGE = "org.mockito.junit";

    private static String oldPackage = DEFAULT_OLD_PACKAGE;
    private static String newPackage = DEFAULT_NEW_PACKAGE;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println(usage());
            System.exit(2);
            return;
        }
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--from":
                    oldPackage = requireValue(args, ++i);
                    break;
                case "--to":
                    newPackage = requireValue(args, ++i);
                    break;
                case "-h":
                case "--help":
                    System.out.println(usage());
                    return;
                default:
                    sources.add(args[i].trim());
            }
        }
        if (sources.isEmpty() || oldPackage.equals(newPackage)) {
            System.err.println(usage());
            System.exit(2);
            return;
        }

        int total = 0;
        for (String source : sources) {
            // Each input root is its own source root: the pretty-printer writes
            // package-relative files back onto the original compilation units.
            Launcher launcher = new Launcher();
            Environment env = launcher.getEnvironment();
            env.setComplianceLevel(17);
            env.setNoClasspath(true);
            env.setCommentEnabled(true);
            launcher.addInputResource(source);
            launcher.setSourceOutputDirectory(new File(source).getAbsoluteFile());
            launcher.buildModel();

            total += relocateAndPrint(launcher);
        }
        System.out.println("Rewrote " + total + " reference(s) from '" + oldPackage + "' to '"
                + newPackage + "'; affected source files updated in place.");
    }

    /**
     * Core of the rule: relocates every reference that sits under the dependency's
     * old package (code references and imports) and rewrites ONLY the affected
     * compilation units back to disk, leaving every untouched file byte-identical.
     */
    public static int relocateAndPrint(Launcher launcher) {
        Factory factory = launcher.getFactory();
        CtModel model = launcher.getModel();
        String from = oldPackage;
        String to = newPackage;
        Set<String> affected = new HashSet<>();

        int relocatedCount = 0;
        for (CtTypeAccess<?> access : model.getElements(new TypeFilter<>(CtTypeAccess.class))) {
            CtTypeReference<?> relocated = relocatedReference(factory, access.getAccessedType(), from, to);
            if (relocated != null) {
                ((CtTypeAccess) access).setAccessedType(relocated);
                relocatedCount++;
                markAffectedType(access, affected);
            }
        }

        // Imports live on the compilation-unit registry; rewrite matching ones.
        for (spoon.reflect.cu.CompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            if (cu instanceof CtCompilationUnit) {
                CtCompilationUnit unit = (CtCompilationUnit) cu;
                int before = relocatedCount;
                relocatedCount += rewriteImports(unit, from, to);
                if (relocatedCount > before) {
                    for (CtType<?> type : unit.getDeclaredTypes()) {
                        affected.add(type.getQualifiedName());
                    }
                }
            }
        }

        if (relocatedCount == 0) {
            return 0;
        }
        Filter<CtType<?>> onlyAffected = type -> affected.contains(type.getQualifiedName());
        launcher.getModelBuilder().generateProcessedSourceFiles(OutputType.CLASSES, onlyAffected);
        return relocatedCount;
    }

    /** Registers the top-level type whose source was changed, so its file is rewritten. */
    private static void markAffectedType(CtTypeAccess<?> access, Set<String> affected) {
        CtType<?> enclosing = access.getParent(CtType.class);
        while (enclosing != null) {
            affected.add(enclosing.getQualifiedName());
            CtType<?> declaring = enclosing.getDeclaringType();
            enclosing = declaring;
        }
    }
    /** Rewrites the import statements of one compilation unit matching the old API pattern. */
    private static int rewriteImports(CtCompilationUnit unit, String from, String to) {
        int count = 0;
        if (unit == null) {
            return 0;
        }
        for (CtImport imp : unit.getImports()) {
            CtReference ref = imp.getReference();
            if (ref instanceof CtTypeReference) {
                CtTypeReference<?> relocated = relocatedReference(unit.getFactory(), (CtTypeReference<?>) ref, from, to);
                if (relocated != null) {
                    imp.setReference(relocated);
                    count++;
                    continue;
                }
            }
            if (imp instanceof CtUnresolvedImport) {
                CtUnresolvedImport unresolved = (CtUnresolvedImport) imp;
                String text = unresolved.getUnresolvedReference();
                if (text != null && text.startsWith(from + ".")) {
                    unresolved.setUnresolvedReference(to + "." + simpleTail(text));
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns a fresh reference for {@code reference} pointing at the same type
     * name under {@code to}, or null when the reference does not match the old
     * API pattern of a type declared under package {@code from}.
     */
    private static CtTypeReference<?> relocatedReference(Factory factory, CtTypeReference<?> reference,
            String from, String to) {
        if (reference == null || !isDirectlyIn(reference, from)) {
            return null;
        }
        CtTypeReference<?> relocated = factory.Core().createTypeReference();
        relocated.setSimpleName(reference.getSimpleName());
        CtTypeReference<?> declaring = reference.getDeclaringType();
        if (declaring != null) {
            relocated.setDeclaringType(relocatedReference(factory, declaring, from, to));
        } else {
            CtPackageReference pkg = factory.Core().createPackageReference();
            pkg.setSimpleName(to);
            relocated.setPackage(pkg);
        }
        relocated.setSimplyQualified(reference.isSimplyQualified());
        if (!reference.getActualTypeArguments().isEmpty()) {
            relocated.setActualTypeArguments(new ArrayList<>(reference.getActualTypeArguments()));
        }
        return relocated;
    }

    /** Structural matcher: the qualified name lies under {@code pkg} (any depth). */
    private static boolean isDirectlyIn(CtTypeReference<?> reference, String pkg) {
        return reference != null && startsWithPackage(reference.getQualifiedName(), pkg);
    }

    private static boolean startsWithPackage(String qualifiedName, String pkg) {
        return qualifiedName.startsWith(pkg + ".");
    }

    private static String simpleTail(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static String usage() {
        return "Usage: java " + Main.class.getName()
                + " <srcDir1> [srcDir2 ...] [--from <oldPackage>] [--to <newPackage>]\n"
                + "  Relocates every dependency type reference from the old package to\n"
                + "  the new package in the given source directories (rewritten in place).\n"
                + "  Defaults: --from " + DEFAULT_OLD_PACKAGE + " --to " + DEFAULT_NEW_PACKAGE;
    }
}
