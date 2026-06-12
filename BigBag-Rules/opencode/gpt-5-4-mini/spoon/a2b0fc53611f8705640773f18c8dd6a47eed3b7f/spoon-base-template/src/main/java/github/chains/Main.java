package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtLocalVariable;

public final class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new FlywayProcessor());
        launcher.run();
    }

    private static final class FlywayProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public boolean isToBeProcessed(final CtLocalVariable<?> variable) {
            return isFlywayVariable(variable) && variable.getAssignment() != null;
        }

        @Override
        public void process(final CtLocalVariable<?> variable) {
            final CtExpression<?> assignment = variable.getAssignment();
            if (assignment == null) {
                return;
            }

            final CtMethod<?> method = variable.getParent(CtMethod.class);
            if (method == null || method.getBody() == null) {
                return;
            }

            final List<CtStatement> statements = new ArrayList<>(method.getBody().getStatements());
            final int startIndex = statements.indexOf(variable);
            if (startIndex < 0) {
                return;
            }

            final List<SetterCall> setterCalls = new ArrayList<>();
            boolean seenSetter = false;
            for (int i = startIndex + 1; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                final CtInvocation<?> invocation = asInvocation(statement);
                if (invocation == null) {
                    if (seenSetter) {
                        break;
                    }
                    continue;
                }

                if (!isSetterOnVariable(invocation, variable)) {
                    if (seenSetter) {
                        break;
                    }
                    continue;
                }

                final SetterCall setterCall = SetterCall.from(invocation);
                if (setterCall == null) {
                    continue;
                }

                setterCalls.add(setterCall);
                seenSetter = true;
            }

            if (setterCalls.isEmpty()) {
                return;
            }

            final String rebuiltExpression = rebuildExpression(assignment.toString(), setterCalls);
            variable.setAssignment(getFactory().Code().createCodeSnippetExpression(rebuiltExpression));

            for (final SetterCall setterCall : setterCalls) {
                final CtStatement statement = setterCall.statement;
                if (statement != null && statement.isParentInitialized()) {
                    statement.delete();
                }
            }
        }

        private boolean isFlywayVariable(final CtLocalVariable<?> variable) {
            final CtTypeReference<?> type = variable.getType();
            return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
        }

        private CtInvocation<?> asInvocation(final CtStatement statement) {
            if (statement instanceof CtInvocation) {
                return (CtInvocation<?>) statement;
            }
            return statement.getElements(new TypeFilter<>(CtInvocation.class)).stream().findFirst().orElse(null);
        }

        private boolean isSetterOnVariable(final CtInvocation<?> invocation, final CtLocalVariable<?> variable) {
            if (invocation.getTarget() == null || invocation.getExecutable() == null) {
                return false;
            }
            final String methodName = invocation.getExecutable().getSimpleName();
            if (!"setDataSource".equals(methodName)
                    && !"setLocations".equals(methodName)
                    && !"setValidateOnMigrate".equals(methodName)
                    && !"setClassLoader".equals(methodName)) {
                return false;
            }

            return referencesVariable(invocation.getTarget(), variable);
        }

        private boolean referencesVariable(final CtExpression<?> expression, final CtLocalVariable<?> variable) {
            if (expression instanceof CtVariableAccess) {
                final CtVariableAccess<?> access = (CtVariableAccess<?>) expression;
                return access.getVariable() != null && access.getVariable().equals(variable.getReference());
            }
            return expression.getElements(new TypeFilter<>(CtVariableAccess.class)).stream()
                    .anyMatch(access -> access.getVariable() != null && access.getVariable().equals(variable.getReference()));
        }

        private String rebuildExpression(final String baseExpression, final List<SetterCall> setterCalls) {
            final StringBuilder builder = new StringBuilder();
            final SetterCall classLoaderCall = firstCall(setterCalls, "setClassLoader");
            if (classLoaderCall == null) {
                builder.append("org.flywaydb.core.Flyway.configure()");
            } else {
                builder.append("org.flywaydb.core.Flyway.configure(")
                        .append(classLoaderCall.firstArgument)
                        .append(")");
            }

            for (final SetterCall setterCall : setterCalls) {
                final String fluentName;
                if ("setDataSource".equals(setterCall.methodName)) {
                    fluentName = "dataSource";
                } else if ("setLocations".equals(setterCall.methodName)) {
                    fluentName = "locations";
                } else if ("setValidateOnMigrate".equals(setterCall.methodName)) {
                    fluentName = "validateOnMigrate";
                } else if ("setClassLoader".equals(setterCall.methodName)) {
                    continue;
                } else {
                    continue;
                }
                builder.append('.').append(fluentName).append('(').append(setterCall.firstArgument).append(')');
            }

            builder.append(".load()");
            return builder.toString();
        }

        private SetterCall firstCall(final List<SetterCall> setterCalls, final String methodName) {
            for (final SetterCall setterCall : setterCalls) {
                if (methodName.equals(setterCall.methodName)) {
                    return setterCall;
                }
            }
            return null;
        }
    }

    private static final class SetterCall {
        private final CtStatement statement;
        private final String methodName;
        private final String firstArgument;

        private SetterCall(final CtStatement statement, final String methodName, final String firstArgument) {
            this.statement = statement;
            this.methodName = methodName;
            this.firstArgument = firstArgument;
        }

        private static SetterCall from(final CtInvocation<?> invocation) {
            if (invocation.getArguments().isEmpty()) {
                return null;
            }
            return new SetterCall(invocation, invocation.getExecutable().getSimpleName(), invocation.getArguments().get(0).toString());
        }
    }
}
