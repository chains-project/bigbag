package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory path");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : inputDir + "-spoon-out";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        rewriteFlyway(factory, launcher);

        launcher.prettyprint();
    }

    private static void rewriteFlyway(Factory factory, Launcher launcher) {
        final List<CtLocalVariable<?>> localVariables = new ArrayList<>(launcher.getModel().getElements(e -> e instanceof CtLocalVariable<?>));
        for (final CtLocalVariable<?> localVariable : localVariables) {
            rewriteLocalVariable(factory, localVariable);
        }

        final List<CtField<?>> fields = new ArrayList<>(launcher.getModel().getElements(e -> e instanceof CtField<?>));
        for (final CtField<?> field : fields) {
            rewriteFieldInitializer(factory, field);
        }
    }

    private static void rewriteLocalVariable(Factory factory, CtLocalVariable<?> localVariable) {
        if (!isFlywayType(localVariable.getType())) {
            return;
        }

        final CtExpression<?> init = localVariable.getDefaultExpression();
        if (!(init instanceof CtConstructorCall<?>)) {
            return;
        }

        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) init;
        if (!isFlywayType(constructorCall.getType())) {
            return;
        }

        final List<CtInvocation<?>> setters = collectFlywaySetters(localVariable);
        if (setters.isEmpty()) {
            localVariable.replace(factory.createCodeSnippetStatement(buildFlywayDeclaration(localVariable.getType(), localVariable.getSimpleName(), "org.flywaydb.core.Flyway.configure().load()")));
            return;
        }

        final String chain = buildFlywayChain(setters);
        localVariable.replace(factory.createCodeSnippetStatement(buildFlywayDeclaration(localVariable.getType(), localVariable.getSimpleName(), chain)));
        for (final CtInvocation<?> setter : setters) {
            setter.delete();
        }
    }

    private static void rewriteFieldInitializer(Factory factory, CtField<?> field) {
        if (!isFlywayType(field.getType())) {
            return;
        }

        final CtExpression<?> init = field.getDefaultExpression();
        if (!(init instanceof CtConstructorCall<?>)) {
            return;
        }

        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) init;
        if (!isFlywayType(constructorCall.getType())) {
            return;
        }

        field.setDefaultExpression(factory.createCodeSnippetExpression("org.flywaydb.core.Flyway.configure().load()"));
    }

    private static List<CtInvocation<?>> collectFlywaySetters(CtLocalVariable<?> localVariable) {
        final List<CtInvocation<?>> setters = new ArrayList<>();
        final CtBlock<?> block = localVariable.getParent(CtBlock.class);
        if (block == null) {
            return setters;
        }

        final List<CtStatement> statements = new ArrayList<>(block.getStatements());
        final int index = statements.indexOf(localVariable);
        if (index < 0) {
            return setters;
        }

        for (int i = index + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation<?>)) {
                break;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            if (!variableTarget(localVariable, invocation)) {
                break;
            }

            final String methodName = invocation.getExecutable().getSimpleName();
            if (!methodName.startsWith("set") || invocation.getArguments().size() != 1) {
                break;
            }

            if ("setClassLoader".equals(methodName)) {
                continue;
            }

            setters.add(invocation);
        }

        return setters;
    }

    private static boolean variableTarget(CtVariable<?> variable, CtInvocation<?> invocation) {
        final CtExpression<?> target = invocation.getTarget();
        return target != null && variable.getSimpleName().equals(target.toString());
    }

    private static String buildFlywayChain(List<CtInvocation<?>> setters) {
        final StringBuilder chain = new StringBuilder("org.flywaydb.core.Flyway.configure()");
        for (final CtInvocation<?> setter : setters) {
            final String methodName = setter.getExecutable().getSimpleName();
            final String fluentName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            chain.append('.').append(fluentName).append('(').append(setter.getArguments().get(0)).append(')');
        }
        chain.append(".load()");
        return chain.toString();
    }

    private static String buildFlywayDeclaration(CtTypeReference<?> type, String name, String rhs) {
        return type.getQualifiedName() + " " + name + " = " + rhs + ";";
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && FLYWAY_FQN.equals(type.getQualifiedName());
    }
}
