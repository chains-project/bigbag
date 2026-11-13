package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_CONFIG = "org.flywaydb.core.api.configuration.FluentConfiguration";

    private static final Map<String, String> FLUENT_METHODS = new HashMap<>();

    static {
        FLUENT_METHODS.put("setDataSource", "dataSource");
        FLUENT_METHODS.put("setLocations", "locations");
        FLUENT_METHODS.put("setValidateOnMigrate", "validateOnMigrate");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        for (CtMethod<?> method : launcher.getModel().getElements(e -> e instanceof CtMethod).stream().map(CtMethod.class::cast).toList()) {
            CtBlock<?> body = method.getBody();
            if (body == null) {
                continue;
            }
            rewriteFlywayPattern(body, launcher);
        }
    }

    private static void rewriteFlywayPattern(CtBlock<?> body, Launcher launcher) {
        for (CtLocalVariable<?> localVariable : body.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (!isFlywayType(localVariable.getType())) {
                continue;
            }
            if (!(localVariable.getDefaultExpression() instanceof CtConstructorCall<?> constructorCall)) {
                continue;
            }
            if (!isNoArgFlywayConstructor(constructorCall)) {
                continue;
            }

            String variableName = localVariable.getSimpleName();
            String classLoaderArg = null;
            List<CtInvocation<?>> matchedInvocations = new ArrayList<>();

            for (CtInvocation<?> nextInvocation : body.getElements(new TypeFilter<>(CtInvocation.class))) {
                if (!isInvocationOnVariable(nextInvocation, variableName)) {
                    continue;
                }
                String methodName = nextInvocation.getExecutable().getSimpleName();
                if ("setClassLoader".equals(methodName)) {
                    if (nextInvocation.getArguments().size() == 1) {
                        classLoaderArg = nextInvocation.getArguments().get(0).toString();
                    }
                    matchedInvocations.add(nextInvocation);
                    continue;
                }
                if (FLUENT_METHODS.containsKey(methodName) && nextInvocation.getArguments().size() == 1) {
                    matchedInvocations.add(nextInvocation);
                }
            }

            if (matchedInvocations.isEmpty()) {
                continue;
            }

            if (!allCallsInSameMethod(localVariable, matchedInvocations)) {
                continue;
            }

            StringBuilder builder = new StringBuilder();
            if (classLoaderArg == null) {
                builder.append(FLYWAY_TYPE).append(".configure()");
            } else {
                builder.append(FLYWAY_TYPE).append(".configure(").append(classLoaderArg).append(")");
            }

            for (CtInvocation<?> matched : matchedInvocations) {
                String methodName = matched.getExecutable().getSimpleName();
                String fluentName = FLUENT_METHODS.get(methodName);
                if (fluentName == null) {
                    continue;
                }
                builder.append('.').append(fluentName).append('(').append(matched.getArguments().get(0)).append(')');
            }
            builder.append(".load()");

            localVariable.setDefaultExpression(launcher.getFactory().Code().createCodeSnippetExpression(builder.toString()));
            for (CtInvocation<?> matched : matchedInvocations) {
                matched.delete();
            }
        }
    }

    private static boolean allCallsInSameMethod(CtLocalVariable<?> localVariable, List<CtInvocation<?>> invocations) {
        return invocations.stream().allMatch(invocation -> invocation.getParent(CtMethod.class) == localVariable.getParent(CtMethod.class));
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && (FLYWAY_TYPE.equals(type.getQualifiedName()) || "Flyway".equals(type.getSimpleName()));
    }

    private static boolean isNoArgFlywayConstructor(CtConstructorCall<?> constructorCall) {
        CtTypeReference<?> type = constructorCall.getType();
        return type != null
                && (FLYWAY_TYPE.equals(type.getQualifiedName()) || "Flyway".equals(type.getSimpleName()))
                && constructorCall.getArguments().isEmpty();
    }

    private static boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
        if (invocation.getTarget() == null) {
            return false;
        }
        if (!(invocation.getTarget() instanceof CtVariableAccess<?> variableAccess)) {
            return false;
        }
        return variableAccess.getVariable() != null && variableName.equals(variableAccess.getVariable().getSimpleName());
    }
}
