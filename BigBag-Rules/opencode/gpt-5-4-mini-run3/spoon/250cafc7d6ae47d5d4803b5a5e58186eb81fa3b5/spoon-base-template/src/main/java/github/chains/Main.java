package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        final Path input = Path.of(args[0]);
        final Path output = args.length > 1 ? Path.of(args[1]) : input;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new FlywayProcessor());
        launcher.run();
    }

    private static final class FlywayProcessor extends AbstractProcessor<CtBlock<?>> {
        @Override
        public void process(final CtBlock<?> block) {
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

                if (!isOldFlywayConstructor(constructorCall)) {
                    continue;
                }

                final Transformation transformation = collectTransformation(statements, i + 1, localVariable);
                if (transformation == null) {
                    continue;
                }

                final String fluentExpression = buildFluentExpression(transformation);
                localVariable.setAssignment(getFactory().Code().createCodeSnippetExpression(fluentExpression));

                for (final SetterCall setterCall : transformation.setterCalls) {
                    setterCall.statement.delete();
                }
            }
        }

        private Transformation collectTransformation(final List<CtStatement> statements, final int startIndex,
                final CtLocalVariable<?> localVariable) {
            String classLoaderArgument = null;
            final List<SetterCall> setterCalls = new ArrayList<>();
            int index = startIndex;
            while (index < statements.size()) {
                final CtStatement statement = statements.get(index);
                final CtInvocation<?> invocation = asStandaloneInvocation(statement);
                if (invocation == null || !isInvocationOnVariable(invocation, localVariable)) {
                    break;
                }

                final String methodName = invocation.getExecutable().getSimpleName();
                if ("setClassLoader".equals(methodName)) {
                    if (invocation.getArguments().size() == 1) {
                        classLoaderArgument = invocation.getArguments().get(0).toString();
                        setterCalls.add(new SetterCall(statement, null, null));
                        index++;
                        continue;
                    }
                    break;
                }

                final String fluentName = toFluentName(methodName);
                if (fluentName == null) {
                    break;
                }

                setterCalls.add(new SetterCall(statement, fluentName, joinArguments(invocation)));
                index++;
            }

            if (setterCalls.isEmpty()) {
                return null;
            }

            return new Transformation(classLoaderArgument, setterCalls);
        }

        private String buildFluentExpression(final Transformation transformation) {
            final StringBuilder builder = new StringBuilder();
            builder.append(FLYWAY_TYPE);
            if (transformation.classLoaderArgument != null) {
                builder.append(".configure(").append(transformation.classLoaderArgument).append(")");
            } else {
                builder.append(".configure()");
            }

            for (final SetterCall setterCall : transformation.setterCalls) {
                if (setterCall.fluentName == null) {
                    continue;
                }
                builder.append('.').append(setterCall.fluentName).append('(').append(setterCall.arguments).append(')');
            }

            builder.append(".load()");
            return builder.toString();
        }

        private CtInvocation<?> asStandaloneInvocation(final CtStatement statement) {
            if (statement instanceof CtInvocation<?> invocation) {
                return invocation;
            }
            return null;
        }

        private boolean isInvocationOnVariable(final CtInvocation<?> invocation, final CtLocalVariable<?> localVariable) {
            if (invocation.getTarget() == null) {
                return false;
            }
            return invocation.getTarget().toString().equals(localVariable.getSimpleName());
        }

        private boolean isOldFlywayConstructor(final CtConstructorCall<?> constructorCall) {
            return constructorCall.getType() != null && FLYWAY_TYPE.equals(constructorCall.getType().getQualifiedName())
                    && constructorCall.getArguments().isEmpty();
        }

        private boolean isFlywayType(final CtTypeReference<?> typeReference) {
            return typeReference != null && FLYWAY_TYPE.equals(typeReference.getQualifiedName());
        }

        private String joinArguments(final CtInvocation<?> invocation) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < invocation.getArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(invocation.getArguments().get(i));
            }
            return builder.toString();
        }

        private String toFluentName(final String setterName) {
            if (!setterName.startsWith("set") || setterName.length() <= 3) {
                return null;
            }
            final String property = setterName.substring(3);
            return Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }
    }

    private record SetterCall(CtStatement statement, String fluentName, String arguments) {
    }

    private record Transformation(String classLoaderArgument, List<SetterCall> setterCalls) {
    }
}
