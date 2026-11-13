package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected: <input-source-dir> [output-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : "target/spooned";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        for (String entry : inputDir.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) {
                launcher.addInputResource(entry);
            }
        }

        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        transformFlywayLaunchers(launcher.getFactory());
        launcher.prettyprint();
    }

    private static void transformFlywayLaunchers(Factory factory) {
        final List<CtLocalVariable<?>> locals = new ArrayList<>(factory.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> local : locals) {
            rewriteLocalFlyway(local, factory);
        }
    }

    private static void rewriteLocalFlyway(CtLocalVariable<?> local, Factory factory) {
        if (!isOldFlywayConstruction(local.getDefaultExpression())) {
            return;
        }

        final CtBlock<?> block = local.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        final List<CtStatement> statements = block.getStatements();
        final int index = statements.indexOf(local);
        if (index < 0) {
            return;
        }

        String classLoader = null;
        final List<SetterCall> calls = new ArrayList<>();
        for (int i = index + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            final CtInvocation<?> invocation = extractInvocation(statement);
            if (invocation == null) {
                continue;
            }

            final String method = invocation.getExecutable().getSimpleName();
            if (!isFlywaySetter(method) || !targetsVariable(invocation, local)) {
                continue;
            }

            if ("setClassLoader".equals(method) && !invocation.getArguments().isEmpty()) {
                classLoader = render(invocation.getArguments().get(0));
            } else {
                calls.add(new SetterCall(method, new ArrayList<>(invocation.getArguments())));
            }
            statement.delete();
        }

        final String expression = buildConfigurationExpression(classLoader, calls);
        local.setDefaultExpression(factory.createCodeSnippetExpression(expression));
    }

    private static boolean isOldFlywayConstruction(CtExpression<?> expression) {
        if (!(expression instanceof CtConstructorCall<?> constructorCall)) {
            return false;
        }
        final CtTypeReference<?> type = constructorCall.getType();
        return type != null && FLYWAY_FQN.equals(type.getQualifiedName()) && constructorCall.getArguments().isEmpty();
    }

    private static CtInvocation<?> extractInvocation(CtStatement statement) {
        return statement instanceof CtInvocation<?> invocation ? invocation : null;
    }

    private static boolean targetsVariable(CtInvocation<?> invocation, CtVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtVariableRead<?> read) {
            return Objects.equals(read.getVariable().getSimpleName(), variable.getSimpleName());
        }
        return render(target).endsWith(variable.getSimpleName());
    }

    private static boolean isFlywaySetter(String methodName) {
        return switch (methodName) {
            case "setDataSource", "setClassLoader", "setLocations", "setValidateOnMigrate" -> true;
            default -> false;
        };
    }

    private static String buildConfigurationExpression(String classLoader, List<SetterCall> calls) {
        final StringBuilder builder = new StringBuilder();
        if (classLoader == null) {
            builder.append(FLYWAY_FQN).append(".configure()");
        } else {
            builder.append(FLYWAY_FQN).append(".configure(").append(classLoader).append(")");
        }

        for (SetterCall call : calls) {
            builder.append('.').append(mapMethodName(call.methodName())).append('(');
            for (int i = 0; i < call.arguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(render(call.arguments().get(i)));
            }
            builder.append(')');
        }

        return builder.append(".load()").toString();
    }

    private static String mapMethodName(String oldName) {
        return switch (oldName) {
            case "setDataSource" -> "dataSource";
            case "setLocations" -> "locations";
            case "setValidateOnMigrate" -> "validateOnMigrate";
            default -> oldName;
        };
    }

    private static String render(CtExpression<?> expression) {
        return expression == null ? "null" : expression.toString();
    }

    private record SetterCall(String methodName, List<CtExpression<?>> arguments) {
    }
}
