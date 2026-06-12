package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length == 2 ? args[1] : inputDir;

        final Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();
        final List<CtLocalVariable<?>> variables = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> variable : variables) {
            transformVariable(variable);
        }
        launcher.prettyprint();
    }

    private static void transformVariable(CtLocalVariable<?> variable) {
        if (!isFlyway(variable.getType())) {
            return;
        }

        final CtExpression<?> init = variable.getDefaultExpression();
        if (!(init instanceof CtConstructorCall)) {
            return;
        }

        final CtConstructorCall<?> invocation = (CtConstructorCall<?>) init;
        if (!isFlywayConstructor(invocation)) {
            return;
        }

        final CtBlock<?> block = variable.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        final List<CtStatement> statements = block.getStatements();
        final int declarationIndex = statements.indexOf(variable);
        if (declarationIndex < 0) {
            return;
        }

        final List<SetterCall> setterCalls = new ArrayList<>();
        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation)) {
                break;
            }

            final CtInvocation<?> setterInvocation = (CtInvocation<?>) statement;
            if (!isSetterOnVariable(setterInvocation, variable)) {
                break;
            }

            final String methodName = setterInvocation.getExecutable().getSimpleName();
            if (!isSupportedFlywaySetter(methodName)) {
                break;
            }

            setterCalls.add(new SetterCall(methodName, setterInvocation.getArguments()));
        }

        if (setterCalls.isEmpty()) {
            return;
        }

        final String replacement = buildReplacementExpression(setterCalls);
        variable.setDefaultExpression(variable.getFactory().Code().createCodeSnippetExpression(replacement));

        for (int i = statements.size() - 1; i > declarationIndex; i--) {
            final CtStatement statement = statements.get(i);
            if (statement instanceof CtInvocation && isSetterOnVariable((CtInvocation<?>) statement, variable)) {
                statement.delete();
            }
        }
    }

    private static boolean isFlyway(CtTypeReference<?> type) {
        return type != null && (FLYWAY_TYPE.equals(type.getQualifiedName()) || "Flyway".equals(type.getSimpleName()));
    }

    private static boolean isFlywayConstructor(CtConstructorCall<?> invocation) {
        final CtTypeReference<?> targetType = invocation.getType();
        return targetType != null && (FLYWAY_TYPE.equals(targetType.getQualifiedName()) || "Flyway".equals(targetType.getSimpleName()));
    }

    private static boolean isSetterOnVariable(CtInvocation<?> invocation, CtLocalVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        if (target instanceof spoon.reflect.code.CtVariableAccess) {
            final CtVariableReference<?> reference = ((spoon.reflect.code.CtVariableAccess<?>) target).getVariable();
            return reference != null && (reference.equals(variable.getReference())
                    || reference.getSimpleName().equals(variable.getSimpleName()));
        }

        return false;
    }

    private static boolean isSupportedFlywaySetter(String methodName) {
        return "setDataSource".equals(methodName)
                || "setClassLoader".equals(methodName)
                || "setLocations".equals(methodName)
                || "setValidateOnMigrate".equals(methodName);
    }

    private static String buildReplacementExpression(List<SetterCall> setterCalls) {
        final StringBuilder builder = new StringBuilder();
        builder.append(FLYWAY_TYPE).append(".configure(");

        final SetterCall classLoader = findSetter(setterCalls, "setClassLoader");
        if (classLoader != null && !classLoader.arguments.isEmpty()) {
            builder.append(classLoader.arguments.get(0));
        }

        builder.append(")");

        for (SetterCall call : setterCalls) {
            if ("setClassLoader".equals(call.methodName)) {
                continue;
            }

            builder.append('.');
            builder.append(mapSetterToFluentName(call.methodName));
            builder.append('(');
            for (int i = 0; i < call.arguments.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(call.arguments.get(i));
            }
            builder.append(')');
        }

        builder.append(".load()");
        return builder.toString();
    }

    private static SetterCall findSetter(List<SetterCall> setterCalls, String methodName) {
        for (SetterCall call : setterCalls) {
            if (methodName.equals(call.methodName)) {
                return call;
            }
        }
        return null;
    }

    private static String mapSetterToFluentName(String setterName) {
        if ("setDataSource".equals(setterName)) {
            return "dataSource";
        }
        if ("setLocations".equals(setterName)) {
            return "locations";
        }
        if ("setValidateOnMigrate".equals(setterName)) {
            return "validateOnMigrate";
        }
        throw new IllegalArgumentException("Unsupported setter: " + setterName);
    }

    private static final class SetterCall {
        private final String methodName;
        private final List<CtExpression<?>> arguments;

        private SetterCall(String methodName, List<CtExpression<?>> arguments) {
            this.methodName = methodName;
            this.arguments = new ArrayList<>(arguments);
        }
    }
}
