package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    private static final Map<String, String> SETTER_TO_FLUENT = createSetterMap();

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);

        final Launcher launcher = new Launcher();
        for (final Path sourceRoot : findJavaSourceRoots(input)) {
            launcher.addInputResource(sourceRoot.toString());
        }
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();
        transform(launcher.getFactory());
        launcher.prettyprint();
    }

    private static List<Path> findJavaSourceRoots(final Path input) {
        try {
            return Files.walk(input)
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith(Paths.get("src", "main", "java"))
                            || path.endsWith(Paths.get("src", "test", "java")))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to scan input sources: " + input, e);
        }
    }

    private static void transform(final Factory factory) {
        final List<CtLocalVariable<?>> variables = new ArrayList<>(
                factory.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));

        for (final CtLocalVariable<?> variable : variables) {
            rewriteFlywayChainIfNeeded(factory, variable);
        }
    }

    private static void rewriteFlywayChainIfNeeded(final Factory factory, final CtLocalVariable<?> variable) {
        if (!isFlywayType(variable)) {
            return;
        }

        final CtExpression<?> defaultExpression = variable.getDefaultExpression();
        if (defaultExpression == null || !defaultExpression.toString().startsWith("new Flyway()")
                && !defaultExpression.toString().startsWith("new org.flywaydb.core.Flyway()")) {
            return;
        }

        final CtBlock<?> block = variable.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        final List<CtStatement> statements = block.getStatements();
        final int variableIndex = statements.indexOf(variable);
        if (variableIndex < 0) {
            return;
        }

        final List<CtStatement> setterStatements = new ArrayList<>();
        final List<String> fluentCalls = new ArrayList<>();
        String classLoaderArg = null;

        for (int index = variableIndex + 1; index < statements.size(); index++) {
            final CtStatement statement = statements.get(index);
            final String fluentCall = toFluentCall(variable, statement);
            if (fluentCall == null) {
                break;
            }

            setterStatements.add(statement);
            if (fluentCall.startsWith("__CLASSLOADER__")) {
                classLoaderArg = fluentCall.substring("__CLASSLOADER__".length());
            } else {
                fluentCalls.add(fluentCall);
            }
        }

        final String replacement = buildReplacementExpression(defaultExpression, classLoaderArg, fluentCalls);
        variable.setDefaultExpression(factory.Code().createCodeSnippetExpression(replacement));

        for (final CtStatement setterStatement : setterStatements) {
            setterStatement.delete();
        }
    }

    private static String buildReplacementExpression(final CtExpression<?> defaultExpression, final String classLoaderArg,
            final List<String> fluentCalls) {
        final StringBuilder builder = new StringBuilder();
        builder.append(renderConstructorAsConfiguration(defaultExpression, classLoaderArg));
        for (final String fluentCall : fluentCalls) {
            builder.append('.').append(fluentCall);
        }
        builder.append(".load()");
        return builder.toString();
    }

    private static String renderConstructorAsConfiguration(final CtExpression<?> defaultExpression, final String classLoaderArg) {
        final String expression = defaultExpression.toString();
        if (classLoaderArg != null) {
            return "Flyway.configure(" + classLoaderArg + ")";
        }
        if (expression.startsWith("new org.flywaydb.core.Flyway()")) {
            return "org.flywaydb.core.Flyway.configure()";
        }
        return "Flyway.configure()";
    }

    private static String toFluentCall(final CtLocalVariable<?> variable, final CtStatement statement) {
        if (!(statement instanceof CtInvocation<?>)) {
            return null;
        }

        final CtInvocation<?> invocation = (CtInvocation<?>) statement;

        if ("setClassLoader".equals(invocation.getExecutable().getSimpleName())) {
            return "__CLASSLOADER__" + invocation.getArguments().get(0);
        }

        final String fluentName = SETTER_TO_FLUENT.get(invocation.getExecutable().getSimpleName());
        if (fluentName == null) {
            return null;
        }

        if (!(invocation.getTarget() instanceof CtVariableAccess<?>)) {
            return null;
        }

        final CtVariableAccess<?> target = (CtVariableAccess<?>) invocation.getTarget();
        if (!variable.getSimpleName().equals(target.getVariable().getSimpleName())) {
            return null;
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(fluentName).append('(');
        for (int i = 0; i < invocation.getArguments().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(invocation.getArguments().get(i));
        }
        builder.append(')');
        return builder.toString();
    }

    private static boolean isFlywayType(final CtElement element) {
        return element instanceof CtLocalVariable<?> localVariable
                && localVariable.getType() != null
                && FLYWAY_FQN.equals(localVariable.getType().getQualifiedName());
    }

    private static Map<String, String> createSetterMap() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("setClassLoader", "classLoader");
        map.put("setDataSource", "dataSource");
        map.put("setLocations", "locations");
        map.put("setValidateOnMigrate", "validateOnMigrate");
        return map;
    }
}
