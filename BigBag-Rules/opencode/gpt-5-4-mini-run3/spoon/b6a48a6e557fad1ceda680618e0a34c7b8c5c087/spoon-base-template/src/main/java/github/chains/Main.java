package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <maven-project-root> <output-directory>");
        }

        final File projectRoot = new File(args[0]);
        final File outputDirectory = new File(args[1]);

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDirectory);
        thisAddJavaSources(launcher, projectRoot.toPath());
        launcher.buildModel();

        new FlywayTransformer(launcher.getFactory()).rewrite(launcher.getModel());
        launcher.prettyprint();
    }

    private static void thisAddJavaSources(final Launcher launcher, final Path projectRoot) {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FlywayTransformer {
        private final Factory factory;
        private final CtTypeReference<?> flywayType;

        private FlywayTransformer(final Factory factory) {
            this.factory = factory;
            this.flywayType = factory.Type().createReference(FLYWAY_FQN);
        }

        private void rewrite(final spoon.reflect.CtModel model) {
            final List<CtBlock<?>> blocks = new ArrayList<>(model.getElements(new TypeFilter<>(CtBlock.class)));
            for (final CtBlock<?> block : blocks) {
                this.rewriteBlock(block);
            }
        }

        private void rewriteBlock(final CtBlock<?> block) {
            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                final Seed seed = this.matchSeed(statement);
                if (seed == null) {
                    continue;
                }

                final RewritePlan plan = this.collectPlan(statements, i + 1, seed.variableName);
                final String replacement = this.buildReplacement(plan);

                this.applySeed(seed, replacement);
                for (final CtStatement toDelete : plan.statementsToDelete) {
                    toDelete.delete();
                }
            }
        }

        private Seed matchSeed(final CtStatement statement) {
            if (statement instanceof CtLocalVariable) {
                final CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
                final CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
                if (this.isEmptyFlywayConstruction(defaultExpression)) {
                    return new Seed(localVariable.getSimpleName(), localVariable);
                }
            }

            if (statement instanceof CtAssignment) {
                final CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
                final CtExpression<?> assigned = assignment.getAssignment();
                if (this.isEmptyFlywayConstruction(assigned)) {
                    return new Seed(this.targetName(assignment.getAssigned()), assignment);
                }
            }

            return null;
        }

        private boolean isEmptyFlywayConstruction(final CtExpression<?> expression) {
            if (!(expression instanceof CtConstructorCall)) {
                return false;
            }

            final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
            return FLYWAY_FQN.equals(constructorCall.getType().getQualifiedName()) && constructorCall.getArguments().isEmpty();
        }

        private RewritePlan collectPlan(final List<CtStatement> statements, final int startIndex, final String variableName) {
            final RewritePlan plan = new RewritePlan();
            for (int i = startIndex; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                final CtInvocation<?> invocation = this.matchSetter(statement, variableName);
                if (invocation == null) {
                    break;
                }

                plan.statementsToDelete.add(statement);
                final String methodName = invocation.getExecutable().getSimpleName();
                if ("setClassLoader".equals(methodName)) {
                    plan.classLoaderExpression = invocation.getArguments().get(0).toString();
                } else {
                    plan.calls.add(new SetterCall(methodName, invocation.getArguments().get(0).toString()));
                }
            }
            return plan;
        }

        private CtInvocation<?> matchSetter(final CtStatement statement, final String variableName) {
            if (!(statement instanceof CtInvocation)) {
                return null;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            final String methodName = invocation.getExecutable().getSimpleName();
            if (!this.isSupportedSetter(methodName)) {
                return null;
            }

            final CtExpression<?> target = invocation.getTarget();
            if (target == null || !variableName.equals(this.targetName(target))) {
                return null;
            }

            if (invocation.getArguments().size() != 1) {
                return null;
            }

            return invocation;
        }

        private boolean isSupportedSetter(final String methodName) {
            return "setDataSource".equals(methodName)
                    || "setLocations".equals(methodName)
                    || "setValidateOnMigrate".equals(methodName)
                    || "setClassLoader".equals(methodName);
        }

        private String buildReplacement(final RewritePlan plan) {
            final StringBuilder builder = new StringBuilder();
            builder.append(FLYWAY_FQN).append(".configure(");
            if (plan.classLoaderExpression != null) {
                builder.append(plan.classLoaderExpression);
            }
            builder.append(")");

            for (final SetterCall call : plan.calls) {
                builder.append('.');
                if ("setDataSource".equals(call.methodName)) {
                    builder.append("dataSource");
                } else if ("setLocations".equals(call.methodName)) {
                    builder.append("locations");
                } else if ("setValidateOnMigrate".equals(call.methodName)) {
                    builder.append("validateOnMigrate");
                }
                builder.append('(').append(call.argument).append(')');
            }

            builder.append(".load()");
            return builder.toString();
        }

        private void applySeed(final Seed seed, final String replacement) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            final CtExpression replacementExpression = this.factory.Code().createCodeSnippetExpression(replacement);
            replacementExpression.setType(this.flywayType);

            if (seed.statement instanceof CtLocalVariable) {
                ((CtLocalVariable) seed.statement).setDefaultExpression(replacementExpression);
                return;
            }

            final CtAssignment assignment = (CtAssignment) seed.statement;
            assignment.setAssignment(replacementExpression);
        }

        private String targetName(final CtExpression<?> target) {
            if (target instanceof spoon.reflect.code.CtVariableAccess) {
                return ((spoon.reflect.code.CtVariableAccess<?>) target).getVariable().getSimpleName();
            }

            return target == null ? null : target.toString().replace("this.", "");
        }

        private static final class Seed {
            private final String variableName;
            private final CtStatement statement;

            private Seed(final String variableName, final CtStatement statement) {
                this.variableName = variableName;
                this.statement = statement;
            }
        }

        private static final class SetterCall {
            private final String methodName;
            private final String argument;

            private SetterCall(final String methodName, final String argument) {
                this.methodName = methodName;
                this.argument = argument;
            }
        }

        private static final class RewritePlan {
            private final List<CtStatement> statementsToDelete = new ArrayList<>();
            private final List<SetterCall> calls = new ArrayList<>();
            private String classLoaderExpression;
        }
    }
}
