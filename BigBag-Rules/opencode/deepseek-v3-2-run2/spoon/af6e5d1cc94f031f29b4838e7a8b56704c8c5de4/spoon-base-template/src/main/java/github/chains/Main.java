package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.List;

/**
 * GENERIC Spoon transformation that repairs the Flyway &lt;=8 -&gt; Flyway 9 breaking API change.
 *
 * <p>Abstract characterization of the breaking change (NOT specific to any client project):</p>
 *
 * <pre>
 * OLD API pattern (mutating a directly-constructed Flyway instance via JavaBean setters):
 *     T f = new T();          // no-arg constructor
 *     f.setDataSource(...);   // void setters on the Flyway object
 *     f.setLocations(...);
 *     f.setClassLoader(cl);
 *     f.setValidateOnMigrate(b);
 *
 * NEW API pattern (fluent builder returned by a static factory, terminated by load()):
 *     T f = T.configure(cl).dataSource(...).locations(...).validateOnMigrate(b).load();
 *
 * Structural transformation required at every call site:
 *   1. Replace the no-arg constructor call {@code new T()} with the static factory {@code T.configure()}.
 *   2. For every subsequent {@code f.setX(args)} invocation on that same variable, replace it by a
 *      chained {@code .x(args)} call appended to the configure() builder (JavaBean convention:
 *      {@code setFoo -> foo}).
 *   3. The builder must be terminated with {@code .load()} which returns the configured instance of T.
 *   4. Setters whose fluent equivalent is NOT a chained builder method (here: the ClassLoader) must be
 *      routed into the static factory arguments instead ({@code T.configure(cl)}).
 * </pre>
 *
 * <p>The rule is parameterized ONLY by fully-qualified type names / method names that come from the
 * dependency (Flyway), never from the client project. It is applicable to any affected Maven/Java
 * project by simply pointing {@link #main(String[])} at a different input path.</p>
 */
public class Main {

    /** Fully-qualified type that owns the breaking API (comes from the dependency). */
    static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    /** Static factory method on the dependency that returns the fluent builder. */
    static final String CONFIGURE_METHOD = "configure";
    /** Terminal builder method on the dependency that materializes the instance. */
    static final String LOAD_METHOD = "load";
    /** JavaBean setter prefix removed to obtain the fluent builder method name. */
    static final String SETTER_PREFIX = "set";
    /**
     * Setter whose fluent equivalent is NOT a chained builder method on the dependency; its single
     * argument must instead be supplied to the static factory call (Flyway's FluentConfiguration has
     * no {@code classLoader(...)} method, only {@code configure(ClassLoader)}).
     */
    static final String CLASSLOADER_SETTER = "setClassLoader";

    /**
     * A transformation rule instance is fully described by the constants above; the processing logic
     * is intentionally structural (matched by dependency type + JavaBean setter naming), not by any
     * client-side identifiers.
     */
    @SuppressWarnings("rawtypes")
    public static class FlywayMigrateProcessor extends AbstractProcessor<CtLocalVariable> {

        @Override
        public void process(CtLocalVariable localVariable) {
            final CtExpression initializer = (CtExpression) localVariable.getAssignment();

            // (1) Must be a no-arg construction of the dependency's Flyway type: `new Flyway()`.
            if (!(initializer instanceof CtConstructorCall)) {
                return;
            }
            final CtConstructorCall constructorCall = (CtConstructorCall) initializer;
            if (constructorCall.getType() == null
                    || !FLYWAY_TYPE.equals(constructorCall.getType().getQualifiedName())) {
                return;
            }
            if (!constructorCall.getArguments().isEmpty()) {
                return; // already migrated / non-default construction
            }

            // Locate the enclosing block so we can scan sibling statements for `var.setX(...)` calls.
            final CtBlock block = enclosingBlock(localVariable);
            if (block == null) {
                return;
            }

            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            final int declarationIndex = statements.indexOf(localVariable);
            if (declarationIndex < 0) {
                return;
            }

            final String variableName = localVariable.getSimpleName();
            final List<String> chainedCalls = new ArrayList<>();
            String classLoaderArgument = null;
            final List<CtStatement> toRemove = new ArrayList<>();

            // (2) Scan statements AFTER the declaration for JavaBean setters invoked on the same var.
            for (int i = declarationIndex + 1; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (!(statement instanceof CtInvocation)) {
                    continue;
                }
                final CtInvocation invocation = (CtInvocation) statement;
                if (!(invocation.getTarget() instanceof CtVariableRead)) {
                    continue;
                }
                final CtVariableRead target = (CtVariableRead) invocation.getTarget();
                if (target.getVariable() == null || !variableName.equals(target.getVariable().getSimpleName())) {
                    continue;
                }
                final String methodName = invocation.getExecutable().getSimpleName();
                if (methodName == null || !methodName.startsWith(SETTER_PREFIX)
                        || methodName.length() <= SETTER_PREFIX.length()) {
                    continue;
                }

                final List<CtExpression> arguments = new ArrayList<>(invocation.getArguments());
                if (CLASSLOADER_SETTER.equals(methodName)) {
                    // (4) Route the ClassLoader into the static factory arguments, not the chain.
                    if (!arguments.isEmpty()) {
                        classLoaderArgument = arguments.get(0).toString();
                    }
                } else {
                    // (2) JavaBean convention: setFoo(...) -> .foo(...)
                    final String builderMethod = decapitalize(methodName.substring(SETTER_PREFIX.length()));
                    final StringBuilder argList = new StringBuilder();
                    for (int a = 0; a < arguments.size(); a++) {
                        if (a > 0) {
                            argList.append(", ");
                        }
                        argList.append(arguments.get(a).toString());
                    }
                    chainedCalls.add(builderMethod + "(" + argList + ")");
                }
                toRemove.add(statement);
            }

            // Nothing to migrate (e.g. instance constructed but never configured via setters).
            if (chainedCalls.isEmpty() && classLoaderArgument == null) {
                return;
            }

            // (1)+(3) Build `FLYWAY_TYPE.configure[(cl)].<setter-chain>.load()`.
            final StringBuilder snippet = new StringBuilder();
            snippet.append(FLYWAY_TYPE).append('.').append(CONFIGURE_METHOD).append('(');
            if (classLoaderArgument != null) {
                snippet.append(classLoaderArgument);
            }
            snippet.append(')');
            for (String chained : chainedCalls) {
                snippet.append('.').append(chained);
            }
            snippet.append('.').append(LOAD_METHOD).append("()");

            final Factory factory = localVariable.getFactory();
            localVariable.setAssignment(factory.Code().createCodeSnippetExpression(snippet.toString()));

            for (CtStatement statement : toRemove) {
                statement.delete();
            }
        }

        /** {@code Foo -> foo} (JavaBean property naming), leaving an already-lowercase name untouched. */
        private static String decapitalize(String name) {
            if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
                return name;
            }
            if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
                return name; // acronym-style (e.g. "URL") is left as-is per JavaBeans rules
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        private static CtBlock enclosingBlock(CtElement element) {
            CtElement current = element.getParent();
            while (current != null && !(current instanceof CtBlock)) {
                current = current.getParent();
            }
            return (CtBlock) current;
        }
    }

    /**
     * Runnable entry point. Args: [0] input (file or directory to transform), [1] output directory,
     * [2] (optional) OS path-separator-separated dependency classpath used to resolve types so the
     * rewritten sources keep their original simple names. Only these inputs vary between projects -
     * the transformation rule itself is project-independent.
     */
    public static void main(String[] args) {
        final String input = args.length > 0 ? args[0] : "src";
        final String output = args.length > 1 ? args[1] : "target/spoon-out";
        final String deps = args.length > 2 ? args[2] : null;

        final Launcher launcher = new Launcher();
        launcher.addInputResource(input);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(8);
        if (deps != null && !deps.isEmpty()) {
            launcher.getEnvironment().setSourceClasspath(deps.split(java.io.File.pathSeparator));
        }
        launcher.addProcessor(new FlywayMigrateProcessor());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
        System.out.println("Flyway 9 migration rule applied. input=" + input + " output=" + output);
    }
}
