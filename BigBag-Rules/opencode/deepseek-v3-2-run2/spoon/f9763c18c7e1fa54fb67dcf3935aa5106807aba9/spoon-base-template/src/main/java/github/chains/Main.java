package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GENERIC, REUSABLE Spoon transformation rule for the "dependency removed a wrapper type"
 * breaking change (acceptance-test-harness 5540: HtmlUnit -> Selenium migration).
 *
 * Abstract characterization (parameterized ONLY by the DEPENDENCY's old API names,
 * never by any client-project identifier):
 *
 *   Old dependency API pattern:
 *     Object raw = ...executeScript(...)            // raw result needing unwrapping
 *     new <WrapperType>(raw)                        // wrapper constructor
 *     wrapperRef.<accessor>()                       // wrapper accessor -> real value
 *   New dependency API pattern:
 *     Object result = ...executeScript(...)         // value returned DIRECTLY
 *     (wrapper type was deleted from the dependency)
 *
 * Structural transformation required at EVERY call site, in any project:
 *   1. new WrapperType(X)        -> X                       (unwrap constructor)
 *   2. X.accessor()              -> X                       (unwrap accessor)
 *   3. declarations of WrapperType -> java.lang.Object       (replace removed type)
 *   4. import WrapperType        -> deleted                  (remove obsolete import)
 *
 * Reusability: to apply to another project affected by the same breaking change,
 * pass only that project's source root (e.g. <module>/src/main/java) as args[0].
 * If a DIFFERENT wrapper type was removed, change only the three dependency-level
 * constants below - the rule logic itself is project-agnostic and structural.
 */
public class Main {

    // ---- Dependency-level parameters (from the OLD dependency API specification) ----
    /** Fully-qualified name of the wrapper type removed by the new dependency version. */
    private static final String REMOVED_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    /** Method of the removed wrapper type that unwrapped the result. */
    private static final String REMOVED_ACCESSOR = "getJavaScriptResult";
    /** Type that replaces declarations of the removed type. */
    private static final String REPLACEMENT_TYPE = "java.lang.Object";

    private static final String REMOVED_SIMPLE_NAME =
            REMOVED_TYPE.substring(REMOVED_TYPE.lastIndexOf('.') + 1);
    private static final String REMOVED_PACKAGE =
            REMOVED_TYPE.substring(0, REMOVED_TYPE.lastIndexOf('.'));

    /** Original source files (as found on disk) touched by a transformation. */
    private final Set<Path> touchedOriginalFiles = new LinkedHashSet<>();
    /** Qualified names of types touched, used to locate their pretty-printed output. */
    private final Set<String> touchedTypeNames = new HashSet<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <deps> github.chains.Main <java-source-root> [output-dir]");
            System.err.println("Generic rule (parameterized by dependency names):");
            System.err.println("  new " + REMOVED_TYPE + "(X)        -> X");
            System.err.println("  X." + REMOVED_ACCESSOR + "()          -> X");
            System.err.println("  " + REMOVED_SIMPLE_NAME + " declarations -> " + REPLACEMENT_TYPE);
            System.err.println("  import " + REMOVED_TYPE + " -> deleted");
            System.exit(1);
        }
        new Main().run(Paths.get(args[0]), args.length > 1 ? Paths.get(args[1]) : null);
    }

    private void run(Path sourceRoot, Path outputDir) throws IOException {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceRoot.toString());
        launcher.getEnvironment().setNoClasspath(true);
        // Auto-imports OFF: keeps untouched imports/formatting stable and prevents
        // the printer from introducing conflicting imports while migrating client code.
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);

        CtModel model = launcher.buildModel();
        System.out.println("Applying generic rule to: " + sourceRoot);
        System.out.println("Removed dependency type:  " + REMOVED_TYPE);

        int ctors = unwrapConstructorCalls(model, launcher);
        int accessors = unwrapAccessorInvocations(model, launcher);
        int retyped = replaceRemovedTypeDeclarations(model, launcher);
        int imports = removeObsoleteImports(model, launcher);

        // Pretty-print to a temp directory, then copy ONLY the touched files back,
        // so untouched sources keep their original formatting byte-for-byte.
        Path tmp = Files.createTempDirectory("spoon-out");
        launcher.setSourceOutputDirectory(tmp.toFile());
        launcher.prettyprint();

        Path out = outputDir != null ? outputDir : sourceRoot;
        int written = 0;
        for (String qualifiedName : touchedTypeNames) {
            Path generated = tmp.resolve(qualifiedName.replace('.', '/') + ".java");
            if (!Files.exists(generated)) {
                continue;
            }
            Path target = findOriginalLocation(qualifiedName);
            Path dest = out.resolve(qualifiedName.replace('.', '/') + ".java");
            if (outputDir == null && target != null) {
                dest = target;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(generated, dest, StandardCopyOption.REPLACE_EXISTING);
            written++;
        }
        deleteRecursive(tmp);

        System.out.println("Transformation finished:");
        System.out.println("  constructor calls unwrapped:      " + ctors);
        System.out.println("  accessor invocations unwrapped:   " + accessors);
        System.out.println("  declarations re-typed:            " + retyped);
        System.out.println("  obsolete imports removed:         " + imports);
        System.out.println("  files updated:                    " + written);
    }

    /** Rule 1: replace 'new RemovedType(X)' with X itself (unwrap the removed wrapper). */
    private int unwrapConstructorCalls(CtModel model, Launcher launcher) {
        List<CtConstructorCall<?>> calls =
                model.getElements(new TypeFilter<>(CtConstructorCall.class));
        int count = 0;
        for (CtConstructorCall<?> call : calls) {
            if (isRemovedType(call.getType()) && !call.getArguments().isEmpty()) {
                markTouched(call);
                CtExpression<?> replacement = call.getArguments().get(0).clone();
                call.replace(replacement);
                count++;
            }
        }
        return count;
    }

    /** Rule 2: replace 'X.removedAccessor()' with its receiver X (unwrap the accessor). */
    private int unwrapAccessorInvocations(CtModel model, Launcher launcher) {
        List<CtInvocation<?>> invocations =
                model.getElements(new TypeFilter<>(CtInvocation.class));
        int count = 0;
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getExecutable() == null
                    || !REMOVED_ACCESSOR.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (!isRemovedType(invocation.getExecutable().getDeclaringType())) {
                continue;
            }
            CtExpression<?> target = invocation.getTarget();
            if (target == null || target.isImplicit()) {
                continue;
            }
            markTouched(invocation);
            invocation.replace(target.clone());
            count++;
        }
        return count;
    }

    /** Rule 3: re-type every declaration/cast of the removed type to the raw result type. */
    private int replaceRemovedTypeDeclarations(CtModel model, Launcher launcher) {
        int count = 0;
        CtTypeReference<?> replacement =
                launcher.getFactory().Type().createReference(REPLACEMENT_TYPE);
        List<CtTypedElement<?>> typedElements =
                model.getElements(new TypeFilter<>(CtTypedElement.class));
        for (CtTypedElement<?> element : typedElements) {
            if (isRemovedType(element.getType())) {
                markTouched(element);
                element.setType(replacement.clone());
                count++;
            }
        }
        List<CtTypeAccess<?>> typeAccesses =
                model.getElements(new TypeFilter<>(CtTypeAccess.class));
        for (CtTypeAccess<?> access : typeAccesses) {
            if (isRemovedType(access.getAccessedType())) {
                markTouched(access);
                access.replace(launcher.getFactory().createTypeAccess(replacement.clone()));
                count++;
            }
        }
        return count;
    }

    /** Rule 4: delete imports of the removed dependency type (imports live on compilation units). */
    private int removeObsoleteImports(CtModel model, Launcher launcher) {
        int count = 0;
        for (CtType<?> type : model.getAllTypes()) {
            spoon.reflect.declaration.CtCompilationUnit cu =
                    launcher.getFactory().CompilationUnit().getOrCreate(type);
            for (CtImport imp : List.copyOf(cu.getImports())) {
                if (isRemovedImport(imp)) {
                    markTouched(type);
                    imp.delete();
                    count++;
                }
            }
        }
        return count;
    }

    /** An import matches if its reference resolves to the removed type or its text is that import. */
    private boolean isRemovedImport(CtImport imp) {
        if (imp.getReference() != null && isRemovedTypeReference(imp.getReference())) {
            return true;
        }
        String text = String.valueOf(imp).trim();
        return text.equals("import " + REMOVED_TYPE + ";")
                || text.equals("import static " + REMOVED_TYPE + ";")
                || text.replace(" ", "").equals("import" + REMOVED_TYPE + ";");
    }

    /**
     * Structural match against the removed dependency type. Accepts either the resolved
     * fully-qualified name or (in noClasspath mode, for unresolvable old types) the
     * package + simple-name pair - both taken from the dependency specification.
     */
    private boolean isRemovedType(CtTypeReference<?> typeRef) {
        if (typeRef == null) {
            return false;
        }
        String qualified = typeRef.getQualifiedName();
        if (REMOVED_TYPE.equals(qualified)) {
            return true;
        }
        String simple = typeRef.getSimpleName();
        if (!REMOVED_SIMPLE_NAME.equals(simple)) {
            return false;
        }
        spoon.reflect.reference.CtPackageReference pkg = typeRef.getPackage();
        return pkg == null || pkg.getQualifiedName().isEmpty()
                || REMOVED_PACKAGE.equals(pkg.getQualifiedName());
    }

    /** Import references may be type, package or member references. */
    private boolean isRemovedTypeReference(CtReference reference) {
        if (reference == null) {
            return false;
        }
        String rendered = reference.toString();
        String path;
        try {
            path = String.valueOf(reference.getPath());
        } catch (RuntimeException pathUnavailable) {
            path = rendered;
        }
        if (rendered.contains(REMOVED_TYPE)
                || path.contains(REMOVED_TYPE)
                || path.endsWith("." + REMOVED_TYPE)) {
            return true;
        }
        return reference instanceof CtTypeReference && isRemovedType((CtTypeReference<?>) reference);
    }

    /** Record the enclosing type + original file of a mutated element. */
    private void markTouched(CtElement element) {
        CtType<?> type = element.getParent(CtType.class);
        if (type != null) {
            touchedTypeNames.add(type.getQualifiedName());
        }
        if (element.getPosition() != null && element.getPosition().isValidPosition()) {
            touchedOriginalFiles.add(element.getPosition().getFile().toPath());
        }
    }

    /** Map a qualified type name back to the original file position recorded during mutation. */
    private Path findOriginalLocation(String qualifiedName) {
        String relative = qualifiedName.replace('.', '/') + ".java";
        for (Path original : touchedOriginalFiles) {
            String path = original.toString().replace('\\', '/');
            if (path.endsWith(relative)) {
                return original;
            }
        }
        return null;
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // temp cleanup is best-effort
                    }
                });
        }
    }
}
