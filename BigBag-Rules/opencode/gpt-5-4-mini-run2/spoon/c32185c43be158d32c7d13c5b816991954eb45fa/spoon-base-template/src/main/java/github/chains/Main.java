package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one source directory path");
        }

        final Path inputDir = Paths.get(args[0]);
        final Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final List<CtVariable<?>> candidates = new ArrayList<>();
        for (CtLocalVariable<?> localVariable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            candidates.add(localVariable);
        }
        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<>(CtField.class))) {
            candidates.add(field);
        }

        for (CtVariable<?> variable : candidates) {
            transformFlywayVariable(variable, factory);
        }

        launcher.prettyprint();
    }

    private static void transformFlywayVariable(final CtVariable<?> variable, final Factory factory) {
        final CtExpression<?> initializer = variable.getDefaultExpression();
        if (!(initializer instanceof CtConstructorCall<?>)) {
            return;
        }

        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) initializer;
        if (!isFlywayType(constructorCall.getType())) {
            return;
        }

        final CtElement scope = variable.getParent(CtElement.class);
        final Map<String, CtExpression<?>> setterArguments = new LinkedHashMap<>();
        final CtExpression<?> classLoaderArg = collectSetterCalls(variable, setterArguments);

        final StringBuilder expression = new StringBuilder();
        if (classLoaderArg != null) {
            expression.append(FLYWAY_FQCN).append(".configure(").append(classLoaderArg).append(")");
        } else {
            expression.append(FLYWAY_FQCN).append(".configure()");
        }

        final CtExpression<?> dataSourceArg = setterArguments.get("setDataSource");
        if (dataSourceArg != null) {
            expression.append(".dataSource(").append(dataSourceArg).append(")");
        }

        final CtExpression<?> locationsArg = setterArguments.get("setLocations");
        if (locationsArg != null) {
            expression.append(".locations(").append(locationsArg).append(")");
        }

        final CtExpression<?> validateArg = setterArguments.get("setValidateOnMigrate");
        if (validateArg != null) {
            expression.append(".validateOnMigrate(").append(validateArg).append(")");
        }

        expression.append(".load()");
        variable.setDefaultExpression(factory.Code().createCodeSnippetExpression(expression.toString()));

        if (scope instanceof CtStatement) {
            // no-op: scopes are handled by collected setter deletions below
        }
    }

    private static CtExpression<?> collectSetterCalls(final CtVariable<?> variable, final Map<String, CtExpression<?>> setterArguments) {
        final CtElement parent = variable.getParent();
        if (!(parent instanceof spoon.reflect.code.CtBlock)) {
            return null;
        }

        CtExpression<?> classLoaderArg = null;
        final List<CtStatement> statements = new ArrayList<>(((spoon.reflect.code.CtBlock<?>) parent).getStatements());
        for (CtStatement statement : statements) {
            if (!(statement instanceof CtInvocation<?>)) {
                continue;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            if (!isInvocationOnVariable(invocation, variable)) {
                continue;
            }

            final String methodName = invocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(methodName) && !invocation.getArguments().isEmpty()) {
                classLoaderArg = invocation.getArguments().get(0);
                statement.delete();
                continue;
            }

            if (invocation.getArguments().isEmpty()) {
                continue;
            }

            if ("setDataSource".equals(methodName) || "setLocations".equals(methodName) || "setValidateOnMigrate".equals(methodName)) {
                setterArguments.put(methodName, invocation.getArguments().get(0));
                statement.delete();
            }
        }

        return classLoaderArg;
    }

    private static boolean isInvocationOnVariable(final CtInvocation<?> invocation, final CtVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableRead<?>)) {
            return false;
        }

        final CtVariableReference<?> reference = ((CtVariableRead<?>) target).getVariable();
        return reference != null && reference.equals(variable.getReference());
    }

    private static boolean isFlywayType(final spoon.reflect.reference.CtTypeReference<?> type) {
        return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
    }
}
