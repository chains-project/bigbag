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
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        final String sourceDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(sourceDir);
        launcher.buildModel();

        for (CtLocalVariable<?> localVariable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            transformLocalVariable(localVariable);
        }

        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<>(CtField.class))) {
            transformField(field);
        }

        launcher.prettyprint();
    }

    private static void transformLocalVariable(CtLocalVariable<?> localVariable) {
        final CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
        if (!isNoArgFlywayConstructor(defaultExpression)) {
            return;
        }

        final CtStatement statement = localVariable;
        final List<CtInvocation<?>> setterInvocations = collectFollowingFlywaySetters(statement, localVariable.getSimpleName());
        if (setterInvocations.isEmpty()) {
            localVariable.setDefaultExpression((CtExpression) createFlywayConfigurationExpression(localVariable, List.of()));
            return;
        }

        localVariable.setDefaultExpression((CtExpression) createFlywayConfigurationExpression(localVariable, setterInvocations));
        for (CtInvocation<?> invocation : setterInvocations) {
            invocation.delete();
        }
    }

    private static void transformField(CtField<?> field) {
        final CtExpression<?> defaultExpression = field.getDefaultExpression();
        if (!isNoArgFlywayConstructor(defaultExpression)) {
            return;
        }

        field.setDefaultExpression((CtExpression) createFlywayConfigurationExpression(field, List.of()));
    }

    private static boolean isNoArgFlywayConstructor(CtExpression<?> expression) {
        if (!(expression instanceof CtConstructorCall<?> constructorCall)) {
            return false;
        }

        final CtTypeReference<?> type = constructorCall.getType();
        return type != null
                && "org.flywaydb.core.Flyway".equals(type.getQualifiedName())
                && constructorCall.getArguments().isEmpty();
    }

    private static List<CtInvocation<?>> collectFollowingFlywaySetters(CtStatement statement, String variableName) {
        final List<CtInvocation<?>> setters = new ArrayList<>();
        final CtStatement parentStatement = statement;
        if (parentStatement.getParent() == null || !(parentStatement.getParent() instanceof spoon.reflect.code.CtBlock<?> block)) {
            return setters;
        }

        final List<CtStatement> statements = new ArrayList<>(block.getStatements());
        final int startIndex = statements.indexOf(statement);
        if (startIndex < 0) {
            return setters;
        }

        for (int i = startIndex + 1; i < statements.size(); i++) {
            final CtStatement candidate = statements.get(i);
            final CtInvocation<?> invocation = asFlywaySetterInvocation(candidate, variableName);
            if (invocation == null) {
                break;
            }
            setters.add(invocation);
        }

        return setters;
    }

    private static CtInvocation<?> asFlywaySetterInvocation(CtStatement statement, String variableName) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }

        final String methodName = invocation.getExecutable().getSimpleName();
        if (!methodName.startsWith("set") || methodName.length() <= 3) {
            return null;
        }

        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableRead<?> variableRead)) {
            return null;
        }

        if (!variableName.equals(variableRead.getVariable().getSimpleName())) {
            return null;
        }

        return invocation;
    }

    private static CtExpression<?> createFlywayConfigurationExpression(CtElement element, List<CtInvocation<?>> setterInvocations) {
        final StringBuilder expression = new StringBuilder("org.flywaydb.core.Flyway.configure(");
        final List<String> chainCalls = new ArrayList<>();

        for (CtInvocation<?> invocation : setterInvocations) {
            final String methodName = invocation.getExecutable().getSimpleName();
            final String fluentName = toFluentName(methodName);
            final String arguments = invocation.getArguments().stream()
                    .map(CtExpression::prettyprint)
                    .collect(java.util.stream.Collectors.joining(", "));

            if ("setClassLoader".equals(methodName)) {
                expression.append(arguments).append(")");
                continue;
            }

            chainCalls.add("." + fluentName + "(" + arguments + ")");
        }

        if (expression.charAt(expression.length() - 1) == '(') {
            expression.append(")");
        }

        for (String call : chainCalls) {
            expression.append(call);
        }

        expression.append(".load()");
        return element.getFactory().createCodeSnippetExpression(expression.toString());
    }

    private static String toFluentName(String setterName) {
        final String stripped = setterName.startsWith("set") && setterName.length() > 3 ? setterName.substring(3) : setterName;
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }
}
