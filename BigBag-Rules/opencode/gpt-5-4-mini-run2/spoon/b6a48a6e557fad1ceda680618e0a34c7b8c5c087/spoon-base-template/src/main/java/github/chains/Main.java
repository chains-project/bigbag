package github.chains;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputSourceDir]");
        }

        final String inputSourceDir = args[0];
        final String outputSourceDir = args.length == 2 ? args[1] : inputSourceDir + "-transformed";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addJavaInputs(launcher, inputSourceDir);
        launcher.setSourceOutputDirectory(outputSourceDir);
        launcher.buildModel();

        final List<CtLocalVariable<?>> candidates = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (final CtLocalVariable<?> variable : candidates) {
            transformFlywayConfiguration(variable);
        }

        launcher.prettyprint();
    }

    private static void addJavaInputs(final Launcher launcher, final String inputSourceDir) {
        final Path root = Paths.get(inputSourceDir);
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Input source dir does not exist: " + inputSourceDir);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to enumerate Java sources under " + inputSourceDir, e);
        }
    }

    private static void transformFlywayConfiguration(final CtLocalVariable<?> variable) {
        if (variable.getType() == null || !FLYWAY_TYPE.equals(variable.getType().getQualifiedName())) {
            return;
        }

        final CtExpression<?> defaultExpression = variable.getDefaultExpression();
        if (!(defaultExpression instanceof CtConstructorCall)) {
            return;
        }

        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
        if (constructorCall.getType() == null || !FLYWAY_TYPE.equals(constructorCall.getType().getQualifiedName())) {
            return;
        }
        if (!constructorCall.getArguments().isEmpty()) {
            return;
        }

        final CtElement parent = variable.getParent();
        if (!(parent instanceof spoon.reflect.code.CtBlock)) {
            return;
        }

        @SuppressWarnings("unchecked")
        final spoon.reflect.code.CtBlock<?> block = (spoon.reflect.code.CtBlock<?>) parent;
        final List<CtStatement> statements = block.getStatements();
        final int declarationIndex = statements.indexOf(variable);
        if (declarationIndex < 0) {
            return;
        }

        final CtInvocation<?> dataSourceCall = findSetterCall(statements, declarationIndex, variable.getSimpleName(), "setDataSource");
        final CtInvocation<?> classLoaderCall = findSetterCall(statements, declarationIndex, variable.getSimpleName(), "setClassLoader");
        final CtInvocation<?> locationsCall = findSetterCall(statements, declarationIndex, variable.getSimpleName(), "setLocations");
        final CtInvocation<?> validateOnMigrateCall = findSetterCall(statements, declarationIndex, variable.getSimpleName(), "setValidateOnMigrate");

        if (dataSourceCall == null && classLoaderCall == null && locationsCall == null && validateOnMigrateCall == null) {
            return;
        }

        final StringBuilder chain = new StringBuilder();
        if (classLoaderCall != null && !classLoaderCall.getArguments().isEmpty()) {
            chain.append("org.flywaydb.core.Flyway.configure(")
                    .append(classLoaderCall.getArguments().get(0))
                    .append(")");
        } else {
            chain.append("org.flywaydb.core.Flyway.configure()");
        }

        if (dataSourceCall != null && !dataSourceCall.getArguments().isEmpty()) {
            chain.append(".dataSource(")
                    .append(dataSourceCall.getArguments().get(0))
                    .append(")");
        }

        if (locationsCall != null && !locationsCall.getArguments().isEmpty()) {
            chain.append(".locations(")
                    .append(locationsCall.getArguments().get(0))
                    .append(")");
        }

        if (validateOnMigrateCall != null && !validateOnMigrateCall.getArguments().isEmpty()) {
            chain.append(".validateOnMigrate(")
                    .append(validateOnMigrateCall.getArguments().get(0))
                    .append(")");
        }

        chain.append(".load()");
        variable.setDefaultExpression(variable.getFactory().Code().createCodeSnippetExpression(chain.toString()));

        removeMatchingSetterCalls(statements, declarationIndex, variable.getSimpleName());
    }

    private static CtInvocation<?> findSetterCall(final List<CtStatement> statements, final int declarationIndex,
            final String variableName, final String methodName) {
        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            final CtInvocation<?> invocation = asInvocationOnVariable(statement, variableName);
            if (invocation == null) {
                continue;
            }
            if (methodName.equals(invocation.getExecutable().getSimpleName())) {
                return invocation;
            }
        }
        return null;
    }

    private static void removeMatchingSetterCalls(final List<CtStatement> statements, final int declarationIndex,
            final String variableName) {
        for (int i = statements.size() - 1; i > declarationIndex; i--) {
            final CtStatement statement = statements.get(i);
            final CtInvocation<?> invocation = asInvocationOnVariable(statement, variableName);
            if (invocation == null) {
                continue;
            }
            final String methodName = invocation.getExecutable().getSimpleName();
            if ("setDataSource".equals(methodName) || "setClassLoader".equals(methodName)
                    || "setLocations".equals(methodName) || "setValidateOnMigrate".equals(methodName)) {
                statement.delete();
            }
        }
    }

    private static CtInvocation<?> asInvocationOnVariable(final CtStatement statement, final String variableName) {
        if (!(statement instanceof CtInvocation)) {
            return null;
        }

        final CtInvocation<?> invocation = (CtInvocation<?>) statement;
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableAccess)) {
            return null;
        }

        final CtVariableAccess<?> variableAccess = (CtVariableAccess<?>) target;
        if (variableAccess.getVariable() == null) {
            return null;
        }

        return variableName.equals(variableAccess.getVariable().getSimpleName()) ? invocation : null;
    }
}
