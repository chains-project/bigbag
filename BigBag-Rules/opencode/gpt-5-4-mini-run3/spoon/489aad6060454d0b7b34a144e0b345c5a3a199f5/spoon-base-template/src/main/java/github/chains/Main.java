package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setCopyResources(false);
        addJavaSources(launcher, Path.of(args[0]));
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new FlywayConfigurationProcessor());
        launcher.run();
    }

    private static void addJavaSources(Launcher launcher, Path root) {
        final Path mainSources = root.resolve("src/main/java");
        final Path testSources = root.resolve("src/test/java");

        if (Files.isDirectory(mainSources)) {
            launcher.addInputResource(mainSources.toString());
        }
        if (Files.isDirectory(testSources)) {
            launcher.addInputResource(testSources.toString());
        }
        if (!Files.isDirectory(mainSources) && !Files.isDirectory(testSources)) {
            launcher.addInputResource(root.toString());
        }
    }

    private static final class FlywayConfigurationProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> candidate) {
            return isFlywayVariableWithConstructor(candidate);
        }

        @Override
        public void process(CtLocalVariable<?> variable) {
            final CtBlock<?> block = variable.getParent(CtBlock.class);
            if (block == null) {
                return;
            }

            final List<CtStatement> statements = new ArrayList<>(block.getStatements());
            final int declarationIndex = statements.indexOf(variable);
            if (declarationIndex < 0) {
                return;
            }

            final List<CtInvocation<?>> chainedSetters = new ArrayList<>();
            for (int i = declarationIndex + 1; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (!(statement instanceof CtInvocation<?> invocation)) {
                    break;
                }
                if (!isInvocationOnSameFlywayVariable(invocation, variable)) {
                    break;
                }

                final String methodName = invocation.getExecutable().getSimpleName();
                if (!methodName.startsWith("set")) {
                    break;
                }

                chainedSetters.add(invocation);
            }

            if (chainedSetters.isEmpty()) {
                return;
            }

            final String replacement = buildFlywayExpression(variable, chainedSetters);
            @SuppressWarnings({"rawtypes", "unchecked"})
            final CtExpression assignment = variable.getFactory().Code().createCodeSnippetExpression(replacement);
            variable.setAssignment(assignment);

            for (CtInvocation<?> invocation : chainedSetters) {
                block.getStatements().remove(invocation);
            }
        }

        private boolean isFlywayVariableWithConstructor(CtLocalVariable<?> variable) {
            final CtTypeReference<?> type = variable.getType();
            if (!isFlywayType(type)) {
                return false;
            }

            final CtExpression<?> assignment = variable.getAssignment();
            if (!(assignment instanceof CtConstructorCall<?> constructorCall)) {
                return false;
            }

            return isFlywayType(constructorCall.getType()) && constructorCall.getArguments().isEmpty();
        }

        private boolean isInvocationOnSameFlywayVariable(CtInvocation<?> invocation, CtLocalVariable<?> variable) {
            if (invocation.getTarget() == null || invocation.getExecutable() == null) {
                return false;
            }

            final CtTypeReference<?> targetType = invocation.getTarget().getType();
            if (targetType == null || !isFlywayType(targetType)) {
                return false;
            }

            return invocation.getTarget().toString().equals(variable.getSimpleName());
        }

        private String buildFlywayExpression(CtLocalVariable<?> variable, List<CtInvocation<?>> chainedSetters) {
            String classLoaderArgument = null;
            final List<String> fluentCalls = new ArrayList<>();

            for (CtInvocation<?> invocation : chainedSetters) {
                final String methodName = invocation.getExecutable().getSimpleName();
                final String arguments = joinArguments(invocation.getArguments());

                if ("setClassLoader".equals(methodName)) {
                    classLoaderArgument = arguments;
                    continue;
                }

                if (!methodName.startsWith("set") || methodName.length() <= 3) {
                    continue;
                }

                fluentCalls.add(decapitalize(methodName.substring(3)) + "(" + arguments + ")");
            }

            final StringBuilder expression = new StringBuilder();
            expression.append(FLYWAY_FQCN).append(".configure(");
            if (classLoaderArgument != null) {
                expression.append(classLoaderArgument);
            }
            expression.append(")");

            for (String fluentCall : fluentCalls) {
                expression.append('.').append(fluentCall);
            }

            expression.append(".load()");
            return expression.toString();
        }

        private boolean isFlywayType(CtTypeReference<?> type) {
            if (type == null) {
                return false;
            }

            final String qualifiedName = type.getQualifiedName();
            return FLYWAY_FQCN.equals(qualifiedName) || "Flyway".equals(qualifiedName) || qualifiedName.endsWith(".Flyway");
        }

        private String joinArguments(List<CtExpression<?>> arguments) {
            final List<String> rendered = new ArrayList<>();
            for (CtExpression<?> argument : arguments) {
                rendered.add(argument.toString());
            }
            return String.join(", ", rendered);
        }

        private String decapitalize(String value) {
            if (value.isEmpty()) {
                return value;
            }
            if (value.length() == 1) {
                return value.toLowerCase();
            }
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
    }
}
