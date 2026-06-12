package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";
    private static final String CONFIG_FQN = "org.flywaydb.core.api.configuration.FluentConfiguration";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Main <sourceDir> [outputDir]");
        }

        final String sourceDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : sourceDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addInputResources(launcher, sourceDir);
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        for (CtBlock<?> block : launcher.getModel().getElements(new TypeFilter<>(CtBlock.class))) {
            transformBlock(block, factory);
        }

        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
    }

    private static void addInputResources(final Launcher launcher, final String sourceDir) {
        final Path root = Path.of(sourceDir);
        final Path mainJava = root.resolve("src/main/java");
        final Path testJava = root.resolve("src/test/java");

        if (Files.isDirectory(mainJava) || Files.isDirectory(testJava)) {
            if (Files.isDirectory(mainJava)) {
                launcher.addInputResource(mainJava.toString());
            }
            if (Files.isDirectory(testJava)) {
                launcher.addInputResource(testJava.toString());
            }
            return;
        }

        launcher.addInputResource(sourceDir);
    }

    private static void transformBlock(final CtBlock<?> block, final Factory factory) {
        int index = 0;
        while (index < block.getStatements().size()) {
            final CtStatement statement = block.getStatements().get(index);
            if (!transformLocalVariable(block, statement, factory) && !transformAssignment(block, statement, factory)) {
                index++;
                continue;
            }

            index++;
        }
    }

    private static boolean transformLocalVariable(final CtBlock<?> block, final CtStatement statement, final Factory factory) {
        if (!(statement instanceof CtLocalVariable<?> variable)) {
            return false;
        }

        final FlywaySequence sequence = collectSequence(block, statement, variable.getSimpleName(), variable.getType());
        if (sequence == null) {
            return false;
        }

        variable.setDefaultExpression(factory.Code().createCodeSnippetExpression(buildFlywayExpression(sequence)));
        sequence.deleteSetters();
        return true;
    }

    private static boolean transformAssignment(final CtBlock<?> block, final CtStatement statement, final Factory factory) {
        if (!(statement instanceof CtAssignment<?, ?> assignment)) {
            return false;
        }

        final String targetName = variableName(assignment.getAssigned());
        if (targetName == null) {
            return false;
        }

        final FlywaySequence sequence = collectSequence(block, statement, targetName, flywayTypeOf(assignment.getAssigned()));
        if (sequence == null) {
            return false;
        }

        assignment.setAssignment(factory.Code().createCodeSnippetExpression(buildFlywayExpression(sequence)));
        sequence.deleteSetters();
        return true;
    }

    private static FlywaySequence collectSequence(final CtBlock<?> block, final CtStatement anchor, final String variableName,
            final CtTypeReference<?> variableType) {
        if (variableType == null || !FLYWAY_FQN.equals(variableType.getQualifiedName())) {
            return null;
        }

        final int start = block.getStatements().indexOf(anchor);
        if (start < 0) {
            return null;
        }

        final CtExpression<?> initializer = initializerOf(anchor);
        if (!(initializer instanceof CtConstructorCall<?> call) || !isFlywayConstructor(call)) {
            return null;
        }

        final List<CtInvocation<?>> setters = new ArrayList<>();
        final List<CtStatement> statements = block.getStatements();
        for (int i = start + 1; i < statements.size(); i++) {
            final CtStatement candidate = statements.get(i);
            final CtInvocation<?> invocation = flywaySetter(candidate, variableName);
            if (invocation == null) {
                break;
            }
            setters.add(invocation);
        }

        return new FlywaySequence(call, setters);
    }

    private static CtExpression<?> initializerOf(final CtStatement statement) {
        if (statement instanceof CtLocalVariable<?> variable) {
            return variable.getDefaultExpression();
        }
        if (statement instanceof CtAssignment<?, ?> assignment) {
            return assignment.getAssignment();
        }
        return null;
    }

    private static CtTypeReference<?> flywayTypeOf(final CtExpression<?> expression) {
        if (expression == null || expression.getType() == null) {
            return null;
        }
        return expression.getType();
    }

    private static boolean isFlywayConstructor(final CtConstructorCall<?> call) {
        return call.getType() != null
                && FLYWAY_FQN.equals(call.getType().getQualifiedName())
                && call.getArguments().isEmpty();
    }

    private static CtInvocation<?> flywaySetter(final CtStatement statement, final String variableName) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }

        if (!isTargetVariable(invocation.getTarget(), variableName)) {
            return null;
        }

        final String method = invocation.getExecutable().getSimpleName();
        return switch (method) {
            case "setDataSource", "setClassLoader", "setLocations", "setValidateOnMigrate" -> invocation;
            default -> null;
        };
    }

    private static boolean isTargetVariable(final CtExpression<?> target, final String variableName) {
        if (!(target instanceof CtVariableAccess<?> access)) {
            return false;
        }
        return access.getVariable() != null && variableName.equals(access.getVariable().getSimpleName());
    }

    private static String variableName(final CtExpression<?> expression) {
        if (!(expression instanceof CtVariableAccess<?> access) || access.getVariable() == null) {
            return null;
        }
        return access.getVariable().getSimpleName();
    }

    private static String buildFlywayExpression(final FlywaySequence sequence) {
        final StringBuilder builder = new StringBuilder();
        final CtInvocation<?> classLoaderSetter = sequence.lastSetter("setClassLoader");
        if (classLoaderSetter != null) {
            builder.append(FLYWAY_FQN).append(".configure(").append(argument(classLoaderSetter, 0)).append(")");
        } else {
            builder.append(FLYWAY_FQN).append(".configure()");
        }

        for (CtInvocation<?> invocation : sequence.setters) {
            final String method = invocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(method)) {
                continue;
            }
            builder.append('.').append(fluentName(method)).append('(');
            for (int i = 0; i < invocation.getArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(argument(invocation, i));
            }
            builder.append(')');
        }

        builder.append(".load()");
        return builder.toString();
    }

    private static String fluentName(final String setterName) {
        return switch (setterName) {
            case "setDataSource" -> "dataSource";
            case "setLocations" -> "locations";
            case "setValidateOnMigrate" -> "validateOnMigrate";
            default -> setterName;
        };
    }

    private static String argument(final CtInvocation<?> invocation, final int index) {
        return invocation.getArguments().get(index).toString();
    }

    private static final class FlywaySequence {
        private final CtConstructorCall<?> constructorCall;
        private final List<CtInvocation<?>> setters;

        private FlywaySequence(final CtConstructorCall<?> constructorCall, final List<CtInvocation<?>> setters) {
            this.constructorCall = constructorCall;
            this.setters = setters;
        }

        private CtInvocation<?> lastSetter(final String methodName) {
            for (int i = setters.size() - 1; i >= 0; i--) {
                final CtInvocation<?> invocation = setters.get(i);
                if (methodName.equals(invocation.getExecutable().getSimpleName())) {
                    return invocation;
                }
            }
            return null;
        }

        private void deleteSetters() {
            for (CtInvocation<?> invocation : setters) {
                invocation.delete();
            }
        }
    }
}
