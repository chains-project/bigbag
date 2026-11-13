package github.chains;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtVariableReference;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    private static final Map<String, String> SETTER_TO_FLUENT = new LinkedHashMap<>();

    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setLocationsAsStrings", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Main <source-dir> [output-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : inputDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();
        launcher.addProcessor(new FlywayConfigurationProcessor());
        launcher.process();
        launcher.prettyprint();
    }

    private static class FlywayConfigurationProcessor extends AbstractProcessor<CtBlock<?>> {
        @Override
        public void process(CtBlock<?> block) {
            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            final List<CtStatement> removed = new ArrayList<>();

            for (int i = 0; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);

                if (statement instanceof CtLocalVariable<?>) {
                    final CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
                    if (!isFlywayType(localVariable.getType())) {
                        continue;
                    }

                    final FlywayRewrite rewrite = collectRewrite(statements, i, localVariable.getReference());
                    if (rewrite == null) {
                        continue;
                    }

                    applyRewrite(localVariable, rewrite, removed);
                }

                if (statement instanceof CtAssignment<?, ?>) {
                    final CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
                    if (!(assignment.getAssignment() instanceof CtConstructorCall<?>)) {
                        continue;
                    }

                    final CtConstructorCall<?> ctor = (CtConstructorCall<?>) assignment.getAssignment();
                    if (!isFlywayConstructor(ctor)) {
                        continue;
                    }

                    final CtExpression<?> assigned = assignment.getAssigned();
                    if (!(assigned instanceof CtVariableRead<?>)) {
                        continue;
                    }

                    final CtVariableRead<?> variableRead = (CtVariableRead<?>) assigned;
                    final FlywayRewrite rewrite = collectRewrite(statements, i, variableRead.getVariable());
                    if (rewrite == null) {
                        continue;
                    }

                    assignment.setAssignment((CtExpression) createSnippet(rewrite.render()));
                    removeStatements(removed, rewrite.statementsToRemove);
                }
            }

            removeStatements(block, removed);
        }

        private void applyRewrite(CtLocalVariable<?> localVariable, FlywayRewrite rewrite, List<CtStatement> removed) {
            localVariable.setDefaultExpression((CtExpression) createSnippet(rewrite.render()));
            removeStatements(removed, rewrite.statementsToRemove);
        }

        private FlywayRewrite collectRewrite(List<CtStatement> statements, int startIndex, CtVariableReference<?> variable) {
            final CtStatement start = statements.get(startIndex);
            final FlywayRewrite rewrite = new FlywayRewrite();

            if (start instanceof CtLocalVariable<?>) {
                final CtLocalVariable<?> localVariable = (CtLocalVariable<?>) start;
                if (!(localVariable.getDefaultExpression() instanceof CtConstructorCall<?>)) {
                    return null;
                }
                if (!isFlywayConstructor((CtConstructorCall<?>) localVariable.getDefaultExpression())) {
                    return null;
                }
                rewrite.baseExpression = buildConfigureExpression(localVariable.getDefaultExpression());
            } else if (start instanceof CtAssignment<?, ?>) {
                final CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) start;
                rewrite.baseExpression = buildConfigureExpression(assignment.getAssignment());
            } else {
                return null;
            }

            for (int i = startIndex + 1; i < statements.size(); i++) {
                final CtStatement next = statements.get(i);
                final CtInvocation<?> invocation = asMatchingFlywaySetter(next, variable);
                if (invocation == null) {
                    break;
                }

                final String methodName = invocation.getExecutable().getSimpleName();
                if ("setClassLoader".equals(methodName)) {
                    rewrite.baseExpression = "org.flywaydb.core.Flyway.configure(" + invocation.getArguments().get(0).toString() + ")";
                    rewrite.statementsToRemove.add(next);
                    continue;
                }

                final String fluentName = SETTER_TO_FLUENT.get(methodName);
                if (fluentName == null) {
                    break;
                }

                rewrite.chainedCalls.add(fluentName + "(" + joinArguments(invocation) + ")");
                rewrite.statementsToRemove.add(next);
            }

            if (rewrite.chainedCalls.isEmpty()) {
                return null;
            }

            return rewrite;
        }

        private CtInvocation<?> asMatchingFlywaySetter(CtStatement statement, CtVariableReference<?> variable) {
            if (!(statement instanceof CtInvocation<?>)) {
                return null;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            final CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !SETTER_TO_FLUENT.containsKey(executable.getSimpleName()) && !"setClassLoader".equals(executable.getSimpleName())) {
                return null;
            }

            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtVariableRead<?>)) {
                return null;
            }

            final CtVariableRead<?> variableRead = (CtVariableRead<?>) target;
            if (variableRead.getVariable() == null || !variableRead.getVariable().getSimpleName().equals(variable.getSimpleName())) {
                return null;
            }

            return invocation;
        }

        private boolean isFlywayType(CtTypeReference<?> type) {
            return type != null && FLYWAY_FQN.equals(type.getQualifiedName());
        }

        private boolean isFlywayConstructor(CtConstructorCall<?> call) {
            final CtTypeReference<?> type = call.getType();
            return type != null && FLYWAY_FQN.equals(type.getQualifiedName());
        }

        private CtExpression<?> createSnippet(String expression) {
            return getFactory().Code().createCodeSnippetExpression(expression);
        }

        private String buildConfigureExpression(CtExpression<?> constructorExpression) {
            return "org.flywaydb.core.Flyway.configure()";
        }

        private String joinArguments(CtInvocation<?> invocation) {
            final List<String> args = new ArrayList<>();
            for (CtExpression<?> argument : invocation.getArguments()) {
                args.add(argument.toString());
            }
            return String.join(", ", args);
        }

        private void removeStatements(List<CtStatement> statementsToRemove, List<CtStatement> removed) {
            for (CtStatement statement : statementsToRemove) {
                if (!removed.contains(statement)) {
                    removed.add(statement);
                }
            }
        }

        private void removeStatements(CtBlock<?> block, List<CtStatement> removed) {
            for (CtStatement statement : removed) {
                statement.delete();
            }
        }
    }

    private static class FlywayRewrite {
        private String baseExpression;
        private final List<String> chainedCalls = new ArrayList<>();
        private final List<CtStatement> statementsToRemove = new ArrayList<>();

        private String render() {
            final StringBuilder builder = new StringBuilder(baseExpression);
            for (String call : chainedCalls) {
                builder.append('.').append(call);
            }
            builder.append(".load()");
            return builder.toString();
        }
    }
}
