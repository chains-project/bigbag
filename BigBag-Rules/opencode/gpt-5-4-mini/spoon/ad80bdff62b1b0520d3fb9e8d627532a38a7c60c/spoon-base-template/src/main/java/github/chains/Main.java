package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir>");
        }

        final Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();

        for (final CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (isFlywayVariable(variable)) {
                rewriteFlywayUsage(variable, launcher);
            }
        }

        launcher.setSourceOutputDirectory(sourceDir.toFile());
        launcher.prettyprint();
    }

    private static boolean isFlywayVariable(CtLocalVariable<?> variable) {
        final CtTypeReference<?> type = variable.getType();
        return type != null && FLYWAY_TYPE.equals(type.getQualifiedName()) && variable.getDefaultExpression() instanceof CtConstructorCall;
    }

    private static void rewriteFlywayUsage(CtLocalVariable<?> variable, Launcher launcher) {
        final CtBlock<?> block = variable.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        final List<CtInvocation<?>> setters = new ArrayList<>();
        for (final CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isSetterOn(invocation, variable) && isSupportedSetter(invocation.getExecutable().getSimpleName())) {
                setters.add(invocation);
            }
        }

        if (setters.isEmpty()) {
            return;
        }

        CtExpression<?> classLoader = null;
        CtExpression<?> dataSource = null;
        CtExpression<?> locations = null;
        CtExpression<?> validate = null;

        for (final CtInvocation<?> invocation : setters) {
            switch (invocation.getExecutable().getSimpleName()) {
                case "setClassLoader":
                    classLoader = firstArg(invocation);
                    break;
                case "setDataSource":
                    dataSource = firstArg(invocation);
                    break;
                case "setLocations":
                    locations = firstArg(invocation);
                    break;
                case "setValidateOnMigrate":
                    validate = firstArg(invocation);
                    break;
                default:
                    break;
            }
        }

        final StringBuilder snippet = new StringBuilder("org.flywaydb.core.Flyway.configure(");
        if (classLoader != null) {
            snippet.append(classLoader);
        }
        snippet.append(")");
        if (dataSource != null) {
            snippet.append(".dataSource(").append(dataSource).append(")");
        }
        if (locations != null) {
            snippet.append(".locations(").append(locations).append(")");
        }
        if (validate != null) {
            snippet.append(".validateOnMigrate(").append(validate).append(")");
        }
        snippet.append(".load()");

        variable.setDefaultExpression(launcher.getFactory().Code().createCodeSnippetExpression(snippet.toString()));
        for (final CtInvocation<?> invocation : setters) {
            final CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement != null) {
                statement.delete();
            }
        }
    }

    private static boolean isSetterOn(CtInvocation<?> invocation, CtLocalVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        return target instanceof CtVariableAccess && ((CtVariableAccess<?>) target).getVariable().equals(variable.getReference());
    }

    private static boolean isSupportedSetter(String name) {
        return "setClassLoader".equals(name) || "setDataSource".equals(name) || "setLocations".equals(name) || "setValidateOnMigrate".equals(name);
    }

    private static CtExpression<?> firstArg(CtInvocation<?> invocation) {
        return invocation.getArguments().isEmpty() ? null : invocation.getArguments().get(0);
    }
}
