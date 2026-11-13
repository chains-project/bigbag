package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation rule for the breaking change introduced by
 * the dependency update to com.artipie:http v1.0.1.
 *
 * Breaking change (characterized abstractly):
 *  - Old API pattern: the dependency transitively exposed the generic
 *    utility type {@code org.cactoos.map.MapEntry} and clients used it
 *    wherever a {@code Map.Entry<String, String>} value was required
 *    (e.g. {@code new MapEntry<>(name, value)} as an argument of
 *    matcher/constructor calls such as {@code RsHasHeaders}).
 *  - New API pattern: that transitive type was removed; the dependency
 *    now provides its own concrete entry type
 *    {@code com.artipie.http.headers.Header}, which implements
 *    {@code java.util.Map.Entry<String, String>} and offers the same
 *    two-argument {@code (String, String)} constructor.
 *  - Required structural transformation at every call site:
 *    retarget the type reference of {@code new Removed<>(...)} to the
 *    replacement type, drop the (now invalid) generic actual type
 *    arguments, and rewrite the corresponding import.
 *
 * The rule matches the removed dependency type by its fully-qualified
 * name only - it never references any client project type, so it can be
 * applied to ANY Maven project broken by the same update. Only the input
 * source directory needs to change between projects; the two FQNs can
 * also be overridden without recompiling via the system properties
 * {@code -Dapi.removed=<old fqn>} and {@code -Dapi.replacement=<new fqn>}.
 *
 * Usage: {@code java github.chains.Main <output-dir> <project-or-source-dir>...}
 * A directory that looks like a Maven project root is expanded to its
 * {@code src/main/java} and {@code src/test/java} roots automatically.
 */
public final class Main {

    /**
     * Fully-qualified name of the type removed from the dependency API.
     */
    private static final String REMOVED_TYPE =
        System.getProperty("api.removed", "org.cactoos.map.MapEntry");

    /**
     * Fully-qualified name of the replacement type in the new API
     * (same kind of element, same constructor shape).
     */
    private static final String REPLACEMENT_TYPE =
        System.getProperty("api.replacement", "com.artipie.http.headers.Header");

    /**
     * Java language version of the processed sources.
     */
    private static final int COMPLIANCE =
        Integer.getInteger("java.compliance", 11);

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            System.err.printf(
                "Usage: %s <output-dir> <source-root|project-dir>...%n",
                Main.class.getName()
            );
            System.exit(1);
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(COMPLIANCE);
        final List<String> excludes = new ArrayList<>();
        excludes.add("package-info.java");
        excludes.add("**/package-info.java");
        launcher.getEnvironment().setSourceClasspath(new String[0]);
        for (int idx = 1; idx < args.length; idx++) {
            addSources(launcher, new File(args[idx]));
        }
        launcher.setSourceOutputDirectory(new File(args[0]));
        launcher.buildModel();
        final int fixed = transform(launcher.getFactory(), launcher);
        launcher.prettyprint();
        System.out.printf(
            "Rule '%s' -> '%s': %d call site(s) fixed, result in %s%n",
            REMOVED_TYPE, REPLACEMENT_TYPE, fixed,
            new File(args[0]).getAbsolutePath()
        );
    }

    /**
     * Register input resources. Maven-style roots are picked up when the
     * given directory is a project base directory; otherwise the
     * directory itself is used as a source root.
     *
     * @param launcher Spoon launcher
     * @param root Directory given on the command line
     */
    private static void addSources(final Launcher launcher, final File root) {
        boolean maven = false;
        for (final String relative : new String[]{"src/main/java", "src/test/java"}) {
            final File dir = new File(root, relative);
            if (dir.isDirectory()) {
                launcher.addInputResource(dir.getPath());
                maven = true;
            }
        }
        if (!maven) {
            launcher.addInputResource(root.getPath());
        }
    }

    /**
     * Apply the structural rewrite everywhere the removed type occurs.
     *
     * @param factory Spoon model factory
     * @param launcher Launcher holding the built model
     * @return Number of transformed call sites
     */
    private static int transform(final Factory factory, final Launcher launcher) {
        int fixed = 0;
        for (final CtCompilationUnit unit : factory.CompilationUnit().getMap().values()) {
            for (final CtImport imp : new ArrayList<>(unit.getImports())) {
                final CtReference ref = imp.getReference();
                if (ref instanceof CtTypeReference && matches((CtTypeReference<?>) ref)) {
                    unit.getImports().remove(imp);
                    fixed = fixed + 1;
                }
            }
        }
        final CtTypeReference<?> targetRef = replacement(factory);
        for (final CtConstructorCall<?> call : launcher.getModel()
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
            if (matches(call.getType())) {
                call.setType((CtTypeReference) targetRef.clone());
                call.setActualTypeArguments(new ArrayList<>());
                final CtExecutableReference<?> executable = call.getExecutable();
                if (executable != null) {
                    executable.setDeclaringType((CtTypeReference<?>) targetRef.clone());
                }
                fixed = fixed + 1;
            }
        }
        for (final CtTypeAccess<?> access : launcher.getModel()
            .getElements(new TypeFilter<CtTypeAccess<?>>(CtTypeAccess.class))) {
            if (matches(access.getAccessedType())) {
                access.setAccessedType((CtTypeReference) targetRef.clone());
                fixed = fixed + 1;
            }
        }
        return fixed;
    }

    /**
     * @return Fresh type reference to the replacement type
     */
    private static CtTypeReference<?> replacement(final Factory factory) {
        return factory.Type().createReference(REPLACEMENT_TYPE);
    }

    /**
     * @param ref Reference to test
     * @return True if the reference denotes the removed dependency type
     */
    private static boolean matches(final CtTypeReference<?> ref) {
        return ref != null && REMOVED_TYPE.equals(ref.getQualifiedName());
    }
}
