package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <maven-project-root> <output-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new FlywayProcessor());
        launcher.run();
    }

    private static final class FlywayProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public void process(CtLocalVariable<?> localVariable) {
            if (!isFlywayType(localVariable.getType())) {
                return;
            }

            CtExpression<?> assignment = localVariable.getAssignment();
            if (!(assignment instanceof CtConstructorCall<?> constructorCall) || !isNoArgFlywayConstructor(constructorCall)) {
                return;
            }

            CtBlock<?> block = localVariable.getParent(CtBlock.class);
            if (block == null) {
                return;
            }

            List<CtStatement> statements = new ArrayList<>(block.getStatements());
            int declarationIndex = statements.indexOf(localVariable);
            if (declarationIndex < 0) {
                return;
            }

            String classLoaderArgument = null;
            List<FluentCall> fluentCalls = new ArrayList<>();
            List<CtStatement> removableStatements = new ArrayList<>();

            for (int i = declarationIndex + 1; i < statements.size(); i++) {
                CtStatement statement = statements.get(i);
                if (!(statement instanceof CtInvocation<?> invocation) || !isInvocationOnLocalVariable(invocation, localVariable)) {
                    continue;
                }

                String methodName = invocation.getExecutable().getSimpleName();
                if ("setClassLoader".equals(methodName)) {
                    if (classLoaderArgument == null && !invocation.getArguments().isEmpty()) {
                        classLoaderArgument = invocation.getArguments().get(0).toString();
                    }
                    removableStatements.add(statement);
                    continue;
                }

                String fluentMethod = toFluentMethod(methodName);
                if (fluentMethod != null) {
                    fluentCalls.add(new FluentCall(fluentMethod, joinArguments(invocation)));
                    removableStatements.add(statement);
                }
            }

            StringBuilder expression = new StringBuilder();
            expression.append(FLYWAY_FQCN).append(".configure(");
            if (classLoaderArgument != null) {
                expression.append(classLoaderArgument);
            }
            expression.append(")");
            for (FluentCall fluentCall : fluentCalls) {
                expression.append('.').append(fluentCall.methodName).append('(').append(fluentCall.arguments).append(')');
            }
            expression.append(".load()");

            localVariable.setAssignment(localVariable.getFactory().Code().createCodeSnippetExpression(expression.toString()));
            block.getStatements().removeAll(removableStatements);
        }

        private boolean isInvocationOnLocalVariable(CtInvocation<?> invocation, CtLocalVariable<?> localVariable) {
            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtVariableAccess<?> variableAccess) || variableAccess.getVariable() == null) {
                return false;
            }
            return localVariable.getSimpleName().equals(variableAccess.getVariable().getSimpleName());
        }

        private boolean isNoArgFlywayConstructor(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> type = constructorCall.getType();
            return type != null && isFlywayType(type) && constructorCall.getArguments().isEmpty();
        }

        private boolean isFlywayType(CtTypeReference<?> typeReference) {
            return typeReference != null && FLYWAY_FQCN.equals(typeReference.getQualifiedName());
        }

        private String toFluentMethod(String methodName) {
            if ("setDataSource".equals(methodName)) {
                return "dataSource";
            }
            if ("setLocations".equals(methodName)) {
                return "locations";
            }
            if ("setValidateOnMigrate".equals(methodName)) {
                return "validateOnMigrate";
            }
            return null;
        }

        private String joinArguments(CtInvocation<?> invocation) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < invocation.getArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(invocation.getArguments().get(i));
            }
            return builder.toString();
        }

        private record FluentCall(String methodName, String arguments) {
        }
    }
}
