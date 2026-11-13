package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : inputDir + "-spoon-out";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new FlywayConfigurationProcessor());
        launcher.run();
    }

    private static final class FlywayConfigurationProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> variable) {
            return isFlywayConstructor(variable.getDefaultExpression());
        }

        @Override
        public void process(CtLocalVariable<?> variable) {
            final CtExpression<?> defaultExpression = variable.getDefaultExpression();
            if (!isFlywayConstructor(defaultExpression)) {
                return;
            }

            final CtBlockInfo blockInfo = findEnclosingBlockAndIndex(variable);
            final StringHolder classLoader = new StringHolder();
            final StringHolder dataSource = new StringHolder();
            final StringHolder locations = new StringHolder();
            final StringHolder validateOnMigrate = new StringHolder();

            final List<CtStatement> statementsToDelete = new ArrayList<>();
            if (blockInfo != null) {
                final List<CtStatement> statements = blockInfo.block.getStatements();
                for (int i = blockInfo.index + 1; i < statements.size(); i++) {
                    final CtStatement statement = statements.get(i);
                    if (!(statement instanceof CtInvocation<?>)) {
                        break;
                    }

                    final CtInvocation<?> invocation = (CtInvocation<?>) statement;
                    if (!isInvocationOnVariable(invocation, variable)) {
                        break;
                    }

                    final String methodName = invocation.getExecutable().getSimpleName();
                    if ("setClassLoader".equals(methodName) && invocation.getArguments().size() == 1) {
                        classLoader.set(invocation.getArguments().get(0).toString());
                        statementsToDelete.add(statement);
                        continue;
                    }
                    if ("setDataSource".equals(methodName) && invocation.getArguments().size() == 1) {
                        dataSource.set(invocation.getArguments().get(0).toString());
                        statementsToDelete.add(statement);
                        continue;
                    }
                    if ("setLocations".equals(methodName) && invocation.getArguments().size() == 1) {
                        locations.set(invocation.getArguments().get(0).toString());
                        statementsToDelete.add(statement);
                        continue;
                    }
                    if ("setValidateOnMigrate".equals(methodName) && invocation.getArguments().size() == 1) {
                        validateOnMigrate.set(invocation.getArguments().get(0).toString());
                        statementsToDelete.add(statement);
                        continue;
                    }
                    break;
                }
            }

            final StringBuilder replacement = new StringBuilder();
            replacement.append(FLYWAY_TYPE).append(".configure(");
            if (!classLoader.isEmpty()) {
                replacement.append(classLoader.value);
            }
            replacement.append(")");
            if (!dataSource.isEmpty()) {
                replacement.append(".dataSource(").append(dataSource.value).append(")");
            }
            if (!locations.isEmpty()) {
                replacement.append(".locations(").append(locations.value).append(")");
            }
            if (!validateOnMigrate.isEmpty()) {
                replacement.append(".validateOnMigrate(").append(validateOnMigrate.value).append(")");
            }
            replacement.append(".load()");

            variable.setDefaultExpression(variable.getFactory().Code().createCodeSnippetExpression(replacement.toString()));
            for (CtStatement statement : statementsToDelete) {
                statement.delete();
            }
        }

        private boolean isFlywayConstructor(CtExpression<?> expression) {
            if (!(expression instanceof CtConstructorCall<?>)) {
                return false;
            }

            final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
            final CtTypeReference<?> type = constructorCall.getType();
            return type != null && FLYWAY_TYPE.equals(type.getQualifiedName());
        }

        private boolean isInvocationOnVariable(CtInvocation<?> invocation, CtLocalVariable<?> variable) {
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtVariableAccess<?>)) {
                return false;
            }

            return ((CtVariableAccess<?>) target).getVariable() != null
                    && ((CtVariableAccess<?>) target).getVariable().equals(variable.getReference());
        }

        private CtBlockInfo findEnclosingBlockAndIndex(CtLocalVariable<?> variable) {
            final spoon.reflect.code.CtBlock<?> block = variable.getParent(spoon.reflect.code.CtBlock.class);
            if (block == null) {
                return null;
            }

            final List<CtStatement> statements = block.getStatements();
            final int index = statements.indexOf(variable);
            if (index < 0) {
                return null;
            }

            return new CtBlockInfo(block, index);
        }

        private static final class CtBlockInfo {
            private final spoon.reflect.code.CtBlock<?> block;
            private final int index;

            private CtBlockInfo(spoon.reflect.code.CtBlock<?> block, int index) {
                this.block = block;
                this.index = index;
            }
        }

        private static final class StringHolder {
            private String value = "";

            private void set(String value) {
                this.value = value == null ? "" : value;
            }

            private boolean isEmpty() {
                return this.value.isEmpty();
            }
        }
    }
}
