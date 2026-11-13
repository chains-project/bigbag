package github.chains;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Generic, reusable transformation rule for the breaking change introduced by
 * org.apache.maven.shared:maven-dependency-tree 3.2.0.
 *
 * OLD API pattern (any client project, dependency version &lt;= 3.1.x):
 *   new org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder()
 *   // a removed dependency class instantiated through its NO-ARGUMENT constructor
 *
 * NEW API pattern (dependency version 3.2.0):
 *   new org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder(
 *       org.apache.maven.project.ProjectDependenciesResolver)
 *   // the replacement class whose ONLY constructor requires exactly one argument
 *
 * Structural transformation applied at each affected call site:
 *   1. every constructor call targeting the removed dependency type is retargeted
 *      to the replacement type and its argument list is padded with filler
 *      arguments ((ParamFQN) null) until its arity matches the new constructor
 *      signature;
 *   2. every other reference to the removed type (declarations, casts, generic
 *      arguments, instanceof, ...) is renamed to the replacement type;
 *   3. imports of the removed type are rewritten to the replacement type when
 *      resolvable, and deleted otherwise (no-classpath mode), so the rewritten
 *      file never references the removed dependency class.
 *
 * The rule matches purely on the DEPENDENCY's fully-qualified type names and
 * constructor signature (the parameters below) - it contains no client-project
 * identifiers. To reuse it on another affected Maven project, run it with that
 * project's source directories as arguments; it traverses every Java file below
 * the given inputs and rewrites, in place, only the compilation units that
 * actually contain the old API pattern.
 *
 * Usage: java github.chains.Main &lt;source-dir-or-file&gt; [&lt;more-dirs&gt;...]
 */
public class Main {

    // ------------------------------------------------------------------
    // RULE PARAMETERS: fully-qualified names from the CHANGED DEPENDENCY
    // ------------------------------------------------------------------

    /** Dependency type removed by the update (old API). */
    private static final String REMOVED_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";

    /** Dependency type that replaces it (new API). */
    private static final String REPLACEMENT_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";

    /**
     * Ordered fully-qualified parameter types of the replacement type's
     * constructor: DefaultDependencyGraphBuilder(org.apache.maven.project.ProjectDependenciesResolver).
     */
    private static final String[] REPLACEMENT_CONSTRUCTOR_PARAM_TYPES = { "org.apache.maven.project.ProjectDependenciesResolver" };

    /**
     * Filler expression for a parameter the old call sites never passed. The
     * null value is cast to the declared parameter type so the rewritten call
     * stays unambiguous even if the replacement type has several constructors.
     */
    private static String fillerForParameter(String parameterTypeFQN) {
        return "(" + parameterTypeFQN + ") null";
    }

    // ------------------------------------------------------------------

    private static int rewrittenCalls = 0;
    private static int rewrittenReferences = 0;
    private static int rewrittenImports = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: Main <source-dir-or-file> [<more-dirs>...]");
            System.exit(2);
        }

        Launcher launcher = new Launcher();
        for (String input : args) {
            launcher.addInputResource(input);
        }
        // The removed type no longer exists on the updated dependency's classpath,
        // so the model must be built without requiring full resolution.
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setCommentEnabled(true);
        env.setAutoImports(true);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        Set<CtCompilationUnit> modifiedUnits = new LinkedHashSet<>();

        // 1) Retarget + pad every constructor call of the removed type.
        for (CtConstructorCall call : model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class))) {
            if (REMOVED_TYPE.equals(call.getType().getQualifiedName())) {
                rewriteConstructorCall(launcher, call);
                record(launcher, call, modifiedUnits);
                rewrittenCalls++;
            }
        }

        // 2) Rename every remaining reference to the removed type.
        for (CtTypeReference ref : model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class))) {
            if (REMOVED_TYPE.equals(ref.getQualifiedName())) {
                renameReference(launcher, ref);
                record(launcher, ref, modifiedUnits);
                rewrittenReferences++;
            }
        }

        // 3) Fix imports of the removed type: rename when the reference is
        // resolved, otherwise (no-classpath mode leaves them UNRESOLVED) delete
        // the stale import statement so the file cannot reference the removed type.
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            String originalSource = "";
            try {
                originalSource = cu.getOriginalSourceCode() == null ? "" : cu.getOriginalSourceCode();
            } catch (Exception ignored) {
                // no original source available
            }
            for (CtImport imp : new ArrayList<CtImport>(cu.getImports())) {
                CtReference r = imp.getReference();
                boolean stale = false;
                if (r instanceof CtTypeReference && REMOVED_TYPE.equals(((CtTypeReference) r).getQualifiedName())) {
                    renameReference(launcher, (CtTypeReference) r);
                    stale = true;
                } else if ((imp.getImportKind() == CtImportKind.UNRESOLVED || r == null) && !originalSource.isEmpty()) {
                    String text = importText(originalSource, imp);
                    if (text.contains(REMOVED_TYPE)) {
                        imp.delete();
                        stale = true;
                    }
                }
                if (stale) {
                    modifiedUnits.add(cu);
                    rewrittenImports++;
                }
            }
        }

        // 4) Write back only the modified compilation units, in place.
        int filesWritten = 0;
        for (CtCompilationUnit cu : modifiedUnits) {
            File f = cu.getFile();
            if (f == null || !f.isFile()) {
                continue;
            }
            String printed = cu.prettyprint();
            String header = leadingHeader(f);
            if (!header.isEmpty() && !printed.contains(header)) {
                printed = header + "\n" + printed;
            }
            Files.write(f.toPath(), printed.getBytes(StandardCharsets.UTF_8));
            filesWritten++;
            System.out.println("[spoon-rule] rewrote: " + f.getAbsolutePath());
        }

        System.out.println("[spoon-rule] constructor calls rewritten: " + rewrittenCalls);
        System.out.println("[spoon-rule] type references rewritten:  " + rewrittenReferences);
        System.out.println("[spoon-rule] imports rewritten:          " + rewrittenImports);
        System.out.println("[spoon-rule] files updated:              " + filesWritten);
        if (rewrittenCalls + rewrittenReferences + rewrittenImports == 0) {
            System.out.println("[spoon-rule] old API pattern not found; nothing changed.");
        }
    }

    /**
     * new &lt;REMOVED_TYPE&gt;(oldArgs)  ->  new &lt;REPLACEMENT_TYPE&gt;(newArgs),
     * where newArgs = oldArgs padded with fillers up to the new arity.
     */
    private static void rewriteConstructorCall(Launcher launcher, CtConstructorCall call) {
        List<CtExpression<?>> oldArgs = new ArrayList<CtExpression<?>>(call.getArguments());
        int needed = REPLACEMENT_CONSTRUCTOR_PARAM_TYPES.length;
        if (oldArgs.size() > needed) {
            throw new IllegalStateException("Cannot map old constructor call '" + call + "' onto the new constructor signature of "
                    + REPLACEMENT_TYPE + ": the old call passes more arguments than the replacement accepts.");
        }
        renameReference(launcher, call.getType());
        for (int i = oldArgs.size(); i < needed; i++) {
            call.addArgument(launcher.getFactory().Code()
                    .createCodeSnippetExpression(fillerForParameter(REPLACEMENT_CONSTRUCTOR_PARAM_TYPES[i])));
        }
    }

    /** Renames a type reference from REMOVED_TYPE to REPLACEMENT_TYPE. */
    private static void renameReference(Launcher launcher, CtTypeReference ref) {
        int lastDot = REPLACEMENT_TYPE.lastIndexOf('.');
        String pkg = lastDot > 0 ? REPLACEMENT_TYPE.substring(0, lastDot) : "";
        String simple = REPLACEMENT_TYPE.substring(lastDot + 1);
        ref.setPackage(launcher.getFactory().Package().createReference(pkg));
        ref.setDeclaringType((CtTypeReference<?>) null);
        ref.setSimpleName(simple);
        // Print fully qualified so the rewrite is valid regardless of import handling.
        ref.setSimplyQualified(false);
    }

    private static void record(Launcher launcher, CtElement element, Set<CtCompilationUnit> modifiedUnits) {
        try {
            if (element.getPosition().isValidPosition() && element.getPosition().getCompilationUnit() != null) {
                modifiedUnits.add(element.getPosition().getCompilationUnit());
            }
        } catch (Exception ignored) {
            // position unavailable: nothing to record
        }
    }

    /** Original source text of an import statement, used for unresolved imports. */
    private static String importText(String originalSource, CtImport imp) {
        try {
            int start = imp.getPosition().getSourceStart();
            int end = imp.getPosition().getSourceEnd();
            if (start >= 0 && end >= start && end < originalSource.length()) {
                return originalSource.substring(start, end + 1);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    /**
     * Safety net: if the pretty printer dropped a leading file comment (e.g. a
     * license header placed before the package declaration), restore it verbatim
     * from the original file.
     */
    private static String leadingHeader(File original) {
        try {
            String src = new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8);
            int idx = src.indexOf("package ");
            if (idx < 0) {
                idx = src.indexOf("import ");
            }
            if (idx < 0) {
                idx = src.indexOf("class ");
            }
            String header = idx > 0 ? src.substring(0, idx) : "";
            return header.trim().startsWith("/*") ? header : "";
        } catch (Exception e) {
            return "";
        }
    }
}
