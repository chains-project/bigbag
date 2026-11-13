package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: <source-directory> [output-directory]");
        }

        final Path inputPath = Paths.get(args[0]).toAbsolutePath();
        final Path outputPath = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath()
                : inputPath.resolve("spoon-transformed");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputPath.toString());
        launcher.setSourceOutputDirectory(outputPath.toString());
        launcher.addProcessor(new FlywayMigrationProcessor());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class FlywayMigrationProcessor extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> method) {
            final CtBlock<?> body = method.getBody();
            if (body == null) {
                return;
            }

            final List<CtLocalVariable<?>> locals = new ArrayList<>(body.getElements(new TypeFilter<>(CtLocalVariable.class)));
            for (CtLocalVariable<?> local : locals) {
                if (!isOldFlywayConstruction(local)) {
                    continue;
                }

                final String variableName = local.getSimpleName();
                final int localIndex = body.getStatements().indexOf(local);
                if (localIndex < 0) {
                    continue;
                }

                final List<CtInvocation<?>> matchedCalls = new ArrayList<>();
                CtExpression<?> classLoaderArgument = null;
                CtInvocation<?> classLoaderCall = null;

                for (int i = localIndex + 1; i < body.getStatements().size(); i++) {
                    final CtStatement statement = body.getStatements().get(i);
                    final CtInvocation<?> call = asMatchingSetter(statement, variableName);
                    if (call == null) {
                        break;
                    }

                    final String fluentMethod = fluentMethodName(call.getExecutable().getSimpleName());
                    if (fluentMethod == null) {
                        break;
                    }

                    if ("classLoader".equals(fluentMethod)) {
                        classLoaderArgument = call.getArguments().isEmpty() ? null : call.getArguments().get(0);
                        classLoaderCall = call;
                        continue;
                    }

                    matchedCalls.add(call);
                }

                local.setDefaultExpression((CtExpression) buildReplacementExpression(local, classLoaderArgument, matchedCalls));
                if (classLoaderCall != null) {
                    classLoaderCall.delete();
                }
                for (CtInvocation<?> call : matchedCalls) {
                    call.delete();
                }
            }
        }

        private CtExpression<?> buildReplacementExpression(CtLocalVariable<?> local,
                                                           CtExpression<?> classLoaderArgument,
                                                           List<CtInvocation<?>> matchedCalls) {
            final StringBuilder expression = new StringBuilder();
            expression.append(FLYWAY_TYPE).append(".configure");
            if (classLoaderArgument != null) {
                expression.append('(').append(classLoaderArgument).append(')');
            } else {
                expression.append("()");
            }

            for (CtInvocation<?> call : matchedCalls) {
                expression.append('.').append(fluentMethodName(call.getExecutable().getSimpleName()));
                expression.append('(');
                for (int i = 0; i < call.getArguments().size(); i++) {
                    if (i > 0) {
                        expression.append(", ");
                    }
                    expression.append(call.getArguments().get(i));
                }
                expression.append(')');
            }

            expression.append(".load()");
            return local.getFactory().Code().createCodeSnippetExpression(expression.toString());
        }

        private boolean isOldFlywayConstruction(CtLocalVariable<?> local) {
            if (!isFlywayType(local.getType())) {
                return false;
            }

            if (!(local.getDefaultExpression() instanceof CtConstructorCall)) {
                return false;
            }

            final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) local.getDefaultExpression();
            return isFlywayType(constructorCall.getType());
        }

        private boolean isFlywayType(CtTypeReference<?> type) {
            return type != null && FLYWAY_TYPE.equals(type.getQualifiedName());
        }

        private CtInvocation<?> asMatchingSetter(CtStatement statement, String variableName) {
            if (!(statement instanceof CtInvocation)) {
                return null;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            if (!(invocation.getTarget() instanceof CtVariableAccess)) {
                return null;
            }

            final CtVariableAccess<?> target = (CtVariableAccess<?>) invocation.getTarget();
            if (!variableName.equals(target.getVariable().getSimpleName())) {
                return null;
            }

            return fluentMethodName(invocation.getExecutable().getSimpleName()) == null ? null : invocation;
        }

        private String fluentMethodName(String setterName) {
            if (setterName == null || !setterName.startsWith("set") || setterName.length() <= 3) {
                return null;
            }

            final String baseName = setterName.substring(3);
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        }
    }
}
