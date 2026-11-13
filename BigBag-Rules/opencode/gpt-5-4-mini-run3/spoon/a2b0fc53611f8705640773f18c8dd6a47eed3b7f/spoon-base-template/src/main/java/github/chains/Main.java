package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);

        final Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setCopyResources(false);
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            type.getMethods().forEach(method -> {
                final CtBlock<?> body = method.getBody();
                if (body != null) {
                    rewriteBlock(body);
                }
            });
        }
    }

    private static void rewriteBlock(CtBlock<?> body) {
        final List<CtStatement> statements = new ArrayList<>(body.getStatements());
        for (int i = 0; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof spoon.reflect.code.CtLocalVariable<?> localVariable)) {
                continue;
            }
            if (!isFlywayType(localVariable.getType())) {
                continue;
            }
            if (!isNoArgFlyway(localVariable.getDefaultExpression())) {
                continue;
            }

            final List<MethodCall> calls = collectCalls(statements, i + 1, localVariable);
            if (calls.isEmpty()) {
                continue;
            }

            localVariable.setDefaultExpression(localVariable.getFactory().Code().createCodeSnippetExpression(buildExpression(calls)));
            for (int j = calls.size() - 1; j >= 0; j--) {
                calls.get(j).invocation().getParent(CtStatement.class).delete();
            }
        }
    }

    private static List<MethodCall> collectCalls(List<CtStatement> statements, int startIndex, CtVariable<?> variable) {
        final List<MethodCall> calls = new ArrayList<>();
        for (int i = startIndex; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation<?> invocation)) {
                continue;
            }
            if (!isInvocationOnVariable(invocation, variable)) {
                continue;
            }
            final String name = invocation.getExecutable().getSimpleName();
            if (isSupportedSetter(name)) {
                calls.add(new MethodCall(i, invocation));
            }
        }
        return calls;
    }

    private static String buildExpression(List<MethodCall> calls) {
        final List<MethodCall> ordered = calls.stream()
                .sorted(Comparator.comparingInt(MethodCall::index))
                .collect(Collectors.toList());

        final String classLoader = ordered.stream()
                .filter(call -> "setClassLoader".equals(call.name()))
                .map(call -> renderArguments(call.invocation()))
                .findFirst()
                .orElse(null);

        final StringBuilder builder = new StringBuilder();
        builder.append("Flyway.configure(");
        if (classLoader != null) {
            builder.append(classLoader);
        }
        builder.append(")");

        for (MethodCall call : ordered) {
            if ("setClassLoader".equals(call.name())) {
                continue;
            }
            builder.append('.').append(mapMethodName(call.name())).append('(').append(renderArguments(call.invocation())).append(')');
        }
        builder.append(".load()");
        return builder.toString();
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && FLYWAY_TYPE.equals(type.getQualifiedName());
    }

    private static boolean isNoArgFlyway(CtExpression<?> expression) {
        return expression instanceof CtConstructorCall<?> constructorCall
                && isFlywayType(constructorCall.getType())
                && constructorCall.getArguments().isEmpty();
    }

    private static boolean isSupportedSetter(String methodName) {
        return switch (methodName) {
            case "setDataSource", "setClassLoader", "setLocations", "setValidateOnMigrate" -> true;
            default -> false;
        };
    }

    private static boolean isInvocationOnVariable(CtInvocation<?> invocation, CtVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        return target instanceof CtVariableAccess<?> access && Objects.equals(access.getVariable(), variable.getReference());
    }

    private static String mapMethodName(String methodName) {
        return switch (methodName) {
            case "setDataSource" -> "dataSource";
            case "setClassLoader" -> "configure";
            case "setLocations" -> "locations";
            case "setValidateOnMigrate" -> "validateOnMigrate";
            default -> throw new IllegalArgumentException("Unsupported setter: " + methodName);
        };
    }

    private static String renderArguments(CtInvocation<?> invocation) {
        return invocation.getArguments().stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private record MethodCall(int index, CtInvocation<?> invocation) {
        private String name() {
            return invocation.getExecutable() == null ? "" : invocation.getExecutable().getSimpleName();
        }
    }
}
