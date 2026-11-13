package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final String CLASS_LOADER_SETTER = "setClassLoader";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected input source directory as the first argument");
        }

        final String inputPath = args[0];
        final String outputPath = args.length > 1 ? args[1] : inputPath + "-transformed";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        launcher.buildModel();
        rewriteFlywayCallSites(launcher.getFactory());
        launcher.prettyprint();
    }

    private static void rewriteFlywayCallSites(final Factory factory) {
        final List<CtLocalVariable<?>> variables = new ArrayList<>(factory.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));

        for (final CtLocalVariable<?> variable : variables) {
            if (!isFlywayType(variable.getType())) {
                continue;
            }

            final CtExpression<?> defaultExpression = variable.getDefaultExpression();
            if (!(defaultExpression instanceof CtConstructorCall)) {
                continue;
            }

            final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
            if (!isFlywayConstructor(constructorCall)) {
                continue;
            }

            final CtBlock<?> block = variable.getParent(CtBlock.class);
            if (block == null) {
                continue;
            }

            final List<CtStatement> setters = new ArrayList<>();
            CtStatement classLoaderSetter = null;
            boolean seenDeclaration = false;

            for (final CtStatement statement : block.getStatements()) {
                if (statement == variable) {
                    seenDeclaration = true;
                    continue;
                }

                if (!seenDeclaration) {
                    continue;
                }

                if (!(statement instanceof CtInvocation)) {
                    continue;
                }

                final CtInvocation<?> invocation = (CtInvocation<?>) statement;
                if (!isInvocationOnVariable(invocation, variable)) {
                    continue;
                }

                final String methodName = invocation.getExecutable().getSimpleName();
                if (CLASS_LOADER_SETTER.equals(methodName)) {
                    classLoaderSetter = statement;
                } else if (methodName.startsWith("set") && methodName.length() > 3) {
                    setters.add(statement);
                }
            }

            final String replacement = buildReplacementExpression(constructorCall, classLoaderSetter, setters);
            variable.setDefaultExpression(factory.Code().createCodeSnippetExpression(replacement));

            final Set<CtElement> toDelete = new HashSet<>();
            if (classLoaderSetter != null) {
                toDelete.add(classLoaderSetter);
            }
            toDelete.addAll(setters);
            for (final CtElement element : toDelete) {
                element.delete();
            }
        }
    }

    private static boolean isFlywayType(final CtTypeReference<?> type) {
        return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
    }

    private static boolean isFlywayConstructor(final CtConstructorCall<?> constructorCall) {
        final CtExecutableReference<?> executable = constructorCall.getExecutable();
        final CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
        return declaringType != null && FLYWAY_FQCN.equals(declaringType.getQualifiedName()) && constructorCall.getArguments().isEmpty();
    }

    private static boolean isInvocationOnVariable(final CtInvocation<?> invocation, final CtLocalVariable<?> variable) {
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableRead)) {
            return false;
        }

        final CtVariableRead<?> variableRead = (CtVariableRead<?>) target;
        return variableRead.getVariable() != null && variableRead.getVariable().getSimpleName().equals(variable.getSimpleName());
    }

    private static String buildReplacementExpression(final CtConstructorCall<?> constructorCall,
            final CtStatement classLoaderSetter, final List<CtStatement> setters) {
        final StringBuilder expression = new StringBuilder();
        expression.append(FLYWAY_FQCN).append(".configure(");
        if (classLoaderSetter instanceof CtInvocation) {
            final CtInvocation<?> invocation = (CtInvocation<?>) classLoaderSetter;
            if (!invocation.getArguments().isEmpty()) {
                expression.append(renderArguments(invocation.getArguments()));
            }
        }
        expression.append(")");

        for (final CtStatement statement : setters) {
            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            expression.append('.').append(toFluentMethodName(invocation.getExecutable().getSimpleName()));
            expression.append('(').append(renderArguments(invocation.getArguments())).append(')');
        }

        expression.append(".load()");
        return expression.toString();
    }

    private static String toFluentMethodName(final String setterName) {
        if (setterName == null || !setterName.startsWith("set") || setterName.length() < 4) {
            return setterName;
        }

        final char first = Character.toLowerCase(setterName.charAt(3));
        return first + setterName.substring(4);
    }

    private static String renderArguments(final List<CtExpression<?>> arguments) {
        final StringJoiner joiner = new StringJoiner(", ");
        for (final CtExpression<?> argument : arguments) {
            joiner.add(argument.toString());
        }
        return joiner.toString();
    }
}
