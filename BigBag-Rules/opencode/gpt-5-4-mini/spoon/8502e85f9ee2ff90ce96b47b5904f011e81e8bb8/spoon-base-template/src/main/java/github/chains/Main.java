package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final Set<String> SETTER_NAMES = new HashSet<String>(Arrays.asList(
            "setDataSource",
            "setLocations",
            "setValidateOnMigrate",
            "setClassLoader"
    ));

    private Main() {
    }

    public static void main(String[] args) {
        final String projectRoot = args.length > 0 ? args[0] : "/workspace/nem";
        final String outputDir = args.length > 1 ? args[1] : projectRoot;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDir);
        addIfExists(launcher, projectRoot + "/src/main/java");
        addIfExists(launcher, projectRoot + "/src/test/java");
        addIfExists(launcher, projectRoot + "/src/it/java");
        launcher.addProcessor(new FlywayCompatibilityProcessor());
        launcher.run();
    }

    private static void addIfExists(Launcher launcher, String path) {
        if (new java.io.File(path).exists()) {
            launcher.addInputResource(path);
        }
    }

    private static final class FlywayCompatibilityProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> candidate) {
            return candidate.getType() != null && FLYWAY_TYPE.equals(candidate.getType().getQualifiedName())
                    && candidate.getDefaultExpression() instanceof CtConstructorCall;
        }

        @Override
        public void process(CtLocalVariable<?> variable) {
            final CtBlock<?> block = variable.getParent(CtBlock.class);
            if (block == null) {
                return;
            }

            final List<CtStatement> statements = new ArrayList<CtStatement>(block.getStatements());
            final int index = statements.indexOf(variable);
            if (index < 0) {
                return;
            }

            String builder = "org.flywaydb.core.Flyway.configure()";
            final List<CtStatement> consumed = new ArrayList<CtStatement>();
            boolean sawFlywayConfig = false;

            for (int i = index + 1; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (!(statement instanceof CtInvocation)) {
                    break;
                }

                final CtInvocation<?> invocation = (CtInvocation<?>) statement;
                if (!isInvocationOnVariable(invocation, variable.getSimpleName())) {
                    break;
                }

                final String methodName = invocation.getExecutable().getSimpleName();
                if (!SETTER_NAMES.contains(methodName)) {
                    break;
                }

                sawFlywayConfig = true;
                consumed.add(statement);

                if ("setClassLoader".equals(methodName)) {
                    builder = "org.flywaydb.core.Flyway.configure(" + invocation.getArguments().get(0) + ")";
                    continue;
                }

                builder += "." + toFluentName(methodName) + "(" + joinArguments(invocation.getArguments()) + ")";
            }

            if (!sawFlywayConfig) {
                builder = "org.flywaydb.core.Flyway.configure().load()";
            } else {
                builder += ".load()";
            }

            final Factory factory = getFactory();
            final CtExpression<?> replacement = factory.Code().createCodeSnippetExpression(builder);
            variable.setDefaultExpression((CtExpression) replacement);

            for (CtStatement statement : consumed) {
                block.removeStatement(statement);
            }
        }

        private static boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtVariableRead)) {
                return false;
            }
            final CtVariableRead<?> read = (CtVariableRead<?>) target;
            return variableName.equals(read.getVariable().getSimpleName());
        }

        private static String toFluentName(String setterName) {
            if (setterName.startsWith("set") && setterName.length() > 3) {
                return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
            }
            return setterName;
        }

        private static String joinArguments(List<CtExpression<?>> arguments) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(arguments.get(i));
            }
            return builder.toString();
        }
    }
}
