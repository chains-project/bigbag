package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_CONFIGURE = "org.flywaydb.core.Flyway.configure";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        if (args.length == 2) {
            launcher.setSourceOutputDirectory(args[1]);
        }

        launcher.buildModel();
        final FlywayConfigurationTransformer transformer = new FlywayConfigurationTransformer(launcher.getFactory());
        transformer.apply(launcher.getModel());
        launcher.prettyprint();
    }

    private static final class FlywayConfigurationTransformer {
        private final Factory factory;

        private FlywayConfigurationTransformer(Factory factory) {
            this.factory = factory;
        }

        private void apply(CtModel model) {
            final List<CtMethod<?>> methods = new ArrayList<>(model.getElements(new TypeFilter<>(CtMethod.class)));
            for (CtMethod<?> method : methods) {
                transformMethod(method);
            }
        }

        private void transformMethod(CtMethod<?> method) {
            final CtBlock<?> block = method.getBody();
            if (block == null) {
                return;
            }

            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (!(statement instanceof CtLocalVariable<?> localVariable)) {
                    continue;
                }
                if (!isFlywayType(localVariable.getType())) {
                    continue;
                }
                if (!isNoArgFlywayConstruction(localVariable.getDefaultExpression())) {
                    continue;
                }

                final String variableName = localVariable.getSimpleName();
                final List<CtInvocation<?>> setterCalls = new ArrayList<>();
                boolean matched = true;
                int returnIndex = -1;

                for (int j = i + 1; j < statements.size(); j++) {
                    final CtStatement candidate = statements.get(j);
                    if (candidate instanceof CtReturn<?> ctReturn && returnsVariable(ctReturn, variableName)) {
                        returnIndex = j;
                        break;
                    }
                    if (candidate instanceof CtInvocation<?> invocation && isInvocationOnVariable(invocation, variableName)) {
                        if (!isSupportedSetter(invocation)) {
                            matched = false;
                            break;
                        }
                        setterCalls.add(invocation);
                        continue;
                    }
                    matched = false;
                    break;
                }

                if (!matched || returnIndex < 0) {
                    continue;
                }

                final String configuredExpression = buildConfiguredExpression(setterCalls);
                localVariable.setDefaultExpression(factory.Code().createCodeSnippetExpression(configuredExpression));
                for (CtInvocation<?> setterCall : new HashSet<>(setterCalls)) {
                    setterCall.delete();
                }
            }
        }

        private boolean isFlywayType(CtTypeReference<?> type) {
            return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
        }

        private boolean isNoArgFlywayConstruction(CtExpression<?> expression) {
            if (!(expression instanceof spoon.reflect.code.CtConstructorCall<?> constructorCall)) {
                return false;
            }
            final CtTypeReference<?> type = constructorCall.getType();
            if (!isFlywayType(type)) {
                return false;
            }
            return constructorCall.getArguments().isEmpty();
        }

        private boolean returnsVariable(CtReturn<?> ctReturn, String variableName) {
            final CtExpression<?> returnedExpression = ctReturn.getReturnedExpression();
            return returnedExpression instanceof spoon.reflect.code.CtVariableRead<?> variableRead
                    && variableName.equals(variableRead.getVariable().getSimpleName());
        }

        private boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
            final CtExpression<?> target = invocation.getTarget();
            return target instanceof spoon.reflect.code.CtVariableRead<?> variableRead
                    && variableName.equals(variableRead.getVariable().getSimpleName());
        }

        private boolean isSupportedSetter(CtInvocation<?> invocation) {
            final String methodName = invocation.getExecutable().getSimpleName();
            return "setDataSource".equals(methodName)
                    || "setClassLoader".equals(methodName)
                    || "setLocations".equals(methodName)
                    || "setValidateOnMigrate".equals(methodName);
        }

        private String buildConfiguredExpression(List<CtInvocation<?>> setterCalls) {
            String classLoaderExpression = null;
            final List<String> chainedCalls = new ArrayList<>();

            for (CtInvocation<?> invocation : setterCalls) {
                final String methodName = invocation.getExecutable().getSimpleName();
                final List<CtExpression<?>> arguments = invocation.getArguments();
                if ("setClassLoader".equals(methodName)) {
                    if (!arguments.isEmpty()) {
                        classLoaderExpression = arguments.get(0).toString();
                    }
                    continue;
                }

                final String rewrittenName;
                if ("setDataSource".equals(methodName)) {
                    rewrittenName = "dataSource";
                } else if ("setLocations".equals(methodName)) {
                    rewrittenName = "locations";
                } else if ("setValidateOnMigrate".equals(methodName)) {
                    rewrittenName = "validateOnMigrate";
                } else {
                    continue;
                }

                final StringBuilder call = new StringBuilder();
                call.append('.').append(rewrittenName).append('(');
                for (int i = 0; i < arguments.size(); i++) {
                    if (i > 0) {
                        call.append(", ");
                    }
                    call.append(arguments.get(i));
                }
                call.append(')');
                chainedCalls.add(call.toString());
            }

            final StringBuilder builder = new StringBuilder();
            builder.append(FLYWAY_CONFIGURE).append('(');
            if (classLoaderExpression != null) {
                builder.append(classLoaderExpression);
            }
            builder.append(')');
            for (String chainedCall : chainedCalls) {
                builder.append(chainedCall);
            }
            builder.append(".load()");
            return builder.toString();
        }
    }
}
