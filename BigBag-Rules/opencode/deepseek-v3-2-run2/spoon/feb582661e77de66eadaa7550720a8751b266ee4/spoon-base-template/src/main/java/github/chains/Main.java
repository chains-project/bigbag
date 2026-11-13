package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

/**
 * Generic, reusable Spoon transformation for a "library breaking API change"
 * in which a dependency (a) renames / relocates a utility type and (b) removes
 * a class's static no-arg getInstance() factory in favour of a scope-based
 * singleton lookup.
 *
 * The rule is parameterised entirely by FQNs taken FROM THE DEPENDENCY. No
 * client-project identifier appears anywhere in the transformation logic, so
 * the SAME class can migrate ANY Maven/Java project affected by the same
 * breaking change: only argv[0] (the source directory) changes.
 *
 * Usage:  java ... github.chains.Main &lt;srcDir[:srcDir2 ...]&gt; [outDir] [classpath]
 */
public class Main {

    // =====================================================================
    // Dependency-side breaking-change configuration.
    // To re-use this rule on another dependency bump, edit ONLY these
    // constants. They describe the LIBRARY's old-vs-new API, never a
    // client class.
    // =====================================================================

    /** (a) Types that were renamed / relocated: old FQN -&gt; new FQN. */
    public static final Map<String, String> RENAMED_TYPES = new LinkedHashMap<>();

    /** (b) Types whose "public static T getInstance()" was removed. */
    public static final Set<String> SINGLETON_SCOPED_TYPES = new LinkedHashSet<>();

    /** FQN of the new scope / service-locator class that owns singleton access. */
    public static final String SCOPE_TYPE = "liquibase.Scope";
    /** Static method on {@link #SCOPE_TYPE} that returns the current scope. */
    public static final String SCOPE_LOOKUP_METHOD = "getCurrentScope";
    /** Method on the scope instance that returns a singleton given a class literal. */
    public static final String SINGLETON_ACCESSOR_METHOD = "getSingleton";
    /** Name of the removed static factory method on {@link #SINGLETON_SCOPED_TYPES}. */
    public static final String REMOVED_FACTORY_METHOD = "getInstance";

    static {
        // Liquibase 4.8.0: liquibase.util.StringUtils was renamed
        //                  to liquibase.util.StringUtil (same static API surface).
        RENAMED_TYPES.put("liquibase.util.StringUtils", "liquibase.util.StringUtil");
        // Liquibase 4.8.0: ExecutorService.getInstance() was removed; the
        //                  service is now looked up as a singleton through Scope.
        SINGLETON_SCOPED_TYPES.add("liquibase.executor.ExecutorService");
    }

    // =====================================================================
    // Driver
    // =====================================================================
    public static void main(String[] args) throws Exception {
        String input  = args.length > 0 ? args[0] : "./src";
        String output = args.length > 1 ? args[1] : input;
        String cp     = args.length > 2 ? args[2] : "";

        Launcher launcher = new Launcher();
        for (String p : input.split(File.pathSeparator)) {
            launcher.addInputResource(p);
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        if (!cp.isEmpty()) {
            launcher.getEnvironment().setSourceClasspath(cp.split(File.pathSeparator));
        }
        launcher.setSourceOutputDirectory(output);
        launcher.addProcessor(new TypeRenameProcessor());
        launcher.addProcessor(new SingletonFactoryProcessor());
        launcher.buildModel();
        launcher.process();
        cleanObsoleteImports(launcher.getFactory());
        launcher.prettyprint();
    }

    // =====================================================================
    // Rule (a): rewrite every type reference whose FQN matches a RENAMED_TYPES
    // key so that it points at the new dependency-side FQN. This automatically
    // fixes: the import statement, static receivers (CtTypeAccess), declaring
    // types of CtExecutableReference/CtFieldReference, casts, etc. The match
    // is done on the DEPENDENCY FQN, not on any client identifier.
    // =====================================================================
    public static class TypeRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> ref) {
            String oldFqn = ref.getQualifiedName();
            String newFqn = RENAMED_TYPES.get(oldFqn);
            if (newFqn == null) {
                return;
            }
            int dot = newFqn.lastIndexOf('.');
            String pkg = dot > 0 ? newFqn.substring(0, dot) : "";
            String simple = dot > 0 ? newFqn.substring(dot + 1) : newFqn;
            ref.setDeclaringType(null);
            ref.setPackage(getFactory().Package().getOrCreate(pkg).getReference());
            ref.setSimpleName(simple);
        }
    }

    // =====================================================================
    // Rule (b): rewrite the static factory call pattern
    //     T.getInstance()
    // into
    //     SCOPE_TYPE.SCOPE_LOOKUP_METHOD().SINGLETON_ACCESSOR_METHOD(T.class)
    // where T is a FQN in SINGLETON_SCOPED_TYPES. The match is structural:
    //     - no-argument invocation
    //     - method name == REMOVED_FACTORY_METHOD
    //     - declaring / receiver type FQN is in SINGLETON_SCOPED_TYPES
    // Any chained .method(...) after getInstance() is preserved because we
    // replace the innermost invocation node in-place.
    // =====================================================================
    public static class SingletonFactoryProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> inv) {
            CtExecutableReference<?> ex = inv.getExecutable();
            if (ex == null || !REMOVED_FACTORY_METHOD.equals(ex.getSimpleName())) {
                return;
            }
            if (!inv.getArguments().isEmpty()) {
                return;
            }
            String owner = ownerQualifiedType(inv);
            if (owner == null || !SINGLETON_SCOPED_TYPES.contains(owner)) {
                return;
            }

            Factory f = getFactory();
            CtTypeReference<?> scopeRef = f.Type().createReference(SCOPE_TYPE);
            CtTypeReference<?> ownerRef = f.Type().createReference(owner);

            // Build SCOPE_TYPE.SCOPE_LOOKUP_METHOD()
            CtExecutableReference<Object> lookup = f.createExecutableReference();
            lookup.setSimpleName(SCOPE_LOOKUP_METHOD);
            lookup.setDeclaringType(scopeRef);
            lookup.setType(scopeRef);
            lookup.setStatic(true);
            CtInvocation<Object> scopeCall =
                    f.createInvocation(f.createTypeAccess(scopeRef), lookup);

            // Build ....SINGLETON_ACCESSOR_METHOD(T.class)
            CtExecutableReference<Object> getter = f.createExecutableReference();
            getter.setSimpleName(SINGLETON_ACCESSOR_METHOD);
            getter.setDeclaringType(scopeRef);
            getter.setType(ownerRef);
            getter.setStatic(false);
            @SuppressWarnings({"rawtypes", "unchecked"})
            CtExpression classLiteral = (CtExpression) f.Code().createClassAccess(ownerRef);
            CtInvocation<Object> replacement =
                    f.createInvocation(scopeCall, getter, classLiteral);

            inv.replace(replacement);
        }

        /** Structurally resolve the declaring FQN of a static no-arg call. */
        private static String ownerQualifiedType(CtInvocation<?> inv) {
            CtExpression<?> target = inv.getTarget();
            if (target instanceof CtTypeAccess) {
                CtTypeReference<?> tr = ((CtTypeAccess<?>) target).getAccessedType();
                if (tr != null && tr.getQualifiedName() != null && !tr.getQualifiedName().isEmpty()) {
                    return tr.getQualifiedName();
                }
            }
            CtExecutableReference<?> ex = inv.getExecutable();
            if (ex != null) {
                CtTypeReference<?> dt = ex.getDeclaringType();
                if (dt != null && dt.getQualifiedName() != null && !dt.getQualifiedName().isEmpty()) {
                    return dt.getQualifiedName();
                }
            }
            return null;
        }
    }

    // =====================================================================
    // Rule (c): drop import statements that still point at a dependency-side
    // FQN which was renamed. The new name is re-introduced by Spoon's
    // auto-import facility because every use-site type reference has already
    // been rewritten by rule (a). Without this cleanup, the stale old-name
    // import would fail to compile against the new dependency.
    // =====================================================================
    private static void cleanObsoleteImports(Factory f) {
        Set<CtCompilationUnit> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CtType<?> type : f.getModel().getAllTypes()) {
            CtCompilationUnit cu = type.getPosition() != null
                    ? type.getPosition().getCompilationUnit() : null;
            if (cu == null || !seen.add(cu)) {
                continue;
            }
            List<CtImport> kept = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (isObsolete(imp)) {
                    continue;
                }
                kept.add(imp);
            }
            cu.setImports(kept);
        }
    }

    /** True when the import points at a dependency FQN that was renamed. */
    private static boolean isObsolete(CtImport imp) {
        CtReference ref = imp.getReference();
        if (ref instanceof CtTypeReference) {
            return RENAMED_TYPES.containsKey(((CtTypeReference<?>) ref).getQualifiedName());
        }
        // Spoon keeps unresolvable imports (typical when the old type was
        // removed from the new dependency) as an UNRESOLVED CtImport whose
        // printed form is still "import <oldFqn>;". Match on the rendered text.
        String rendered = imp.toString();
        for (String oldFqn : RENAMED_TYPES.keySet()) {
            if (rendered.contains(" " + oldFqn + ";")) {
                return true;
            }
        }
        return false;
    }
}
