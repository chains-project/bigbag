package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final Set<String> SUPPORTED_SETTERS = Set.of(
            "setDataSource",
            "setLocations",
            "setLocationsAsStrings",
            "setValidateOnMigrate",
            "setClassLoader"
    );

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        final List<CtBlock<?>> blocks = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtBlock.class)));
        for (CtBlock<?> block : blocks) {
            transformBlock(block);
        }

        launcher.prettyprint();
    }

    private static void transformBlock(CtBlock<?> block) {
        boolean changed;
        do {
            changed = false;
            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (!(statement instanceof CtLocalVariable<?> localVariable)) {
                    continue;
                }

                if (!isFlywayType(localVariable.getType())) {
                    continue;
                }

                if (!(localVariable.getAssignment() instanceof CtConstructorCall<?> constructorCall)) {
                    continue;
                }

                if (!isZeroArgFlywayConstructor(constructorCall)) {
                    continue;
                }

                final Match match = collectSetterSequence(statements, i + 1, localVariable);
                if (match.setters.isEmpty()) {
                    continue;
                }

                localVariable.setAssignment(localVariable.getFactory().Code().createCodeSnippetExpression(buildInitializer(match)));
                for (CtStatement setterStatement : match.setterStatements) {
                    block.getStatements().remove(setterStatement);
                }
                changed = true;
                break;
            }
        } while (changed);
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && FLYWAY_FQCN.equals(type.getQualifiedName());
    }

    private static boolean isZeroArgFlywayConstructor(CtConstructorCall<?> constructorCall) {
        final CtTypeReference<?> type = constructorCall.getType();
        return type != null && isFlywayType(type) && constructorCall.getArguments().isEmpty();
    }

    private static Match collectSetterSequence(List<CtStatement> statements, int startIndex, CtLocalVariable<?> localVariable) {
        final String variableName = localVariable.getSimpleName();
        final Match match = new Match();

        for (int i = startIndex; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation<?> invocation)) {
                break;
            }

            if (!isInvocationOnVariable(invocation, variableName)) {
                break;
            }

            final String methodName = invocation.getExecutable().getSimpleName();
            if (!SUPPORTED_SETTERS.contains(methodName)) {
                break;
            }

            match.setters.add(invocation);
            match.setterStatements.add(statement);
        }

        return match;
    }

    private static boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
        final CtExpression<?> target = invocation.getTarget();
        return target != null && target.toString().equals(variableName);
    }

    private static String buildInitializer(Match match) {
        final StringBuilder builder = new StringBuilder();
        builder.append("org.flywaydb.core.Flyway.configure(");

        final CtInvocation<?> classLoaderSetter = firstSetter(match.setters, "setClassLoader");
        if (classLoaderSetter != null && !classLoaderSetter.getArguments().isEmpty()) {
            builder.append(classLoaderSetter.getArguments().get(0));
        }
        builder.append(")");

        for (CtInvocation<?> invocation : match.setters) {
            final String methodName = invocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(methodName)) {
                continue;
            }
            builder.append('.').append(toFluentMethod(methodName)).append('(');
            appendArguments(builder, invocation.getArguments());
            builder.append(')');
        }

        builder.append(".load()");
        return builder.toString();
    }

    private static CtInvocation<?> firstSetter(List<CtInvocation<?>> setters, String name) {
        for (CtInvocation<?> setter : setters) {
            if (name.equals(setter.getExecutable().getSimpleName())) {
                return setter;
            }
        }
        return null;
    }

    private static void appendArguments(StringBuilder builder, List<CtExpression<?>> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(arguments.get(i));
        }
    }

    private static String toFluentMethod(String setterName) {
        String name = setterName;
        if (name.startsWith("set") && name.length() > 3) {
            name = name.substring(3);
        }
        if (name.endsWith("AsString")) {
            name = name.substring(0, name.length() - "AsString".length());
        } else if (name.endsWith("AsStrings")) {
            name = name.substring(0, name.length() - "AsStrings".length());
        } else if (name.endsWith("AsFileName")) {
            name = name.substring(0, name.length() - "AsFileName".length());
        } else if (name.endsWith("AsFile")) {
            name = name.substring(0, name.length() - "AsFile".length());
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static final class Match {
        private final List<CtInvocation<?>> setters = new ArrayList<>();
        private final List<CtStatement> setterStatements = new ArrayList<>();
    }
}
