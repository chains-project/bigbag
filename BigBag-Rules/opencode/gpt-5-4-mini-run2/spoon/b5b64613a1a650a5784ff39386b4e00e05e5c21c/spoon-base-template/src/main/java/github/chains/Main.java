package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputSourceDir]");
        }

        String input = args[0];
        String output = args.length > 1 ? args[1] : input + "-transformed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input);
        launcher.setSourceOutputDirectory(output);
        launcher.addProcessor(new ApiBreakProcessor());
        launcher.run();
    }

    private static final class ApiBreakProcessor extends AbstractProcessor<CtElement> {
        private static final String STRING_UTILS = "org.apache.commons.lang3.StringUtils";
        private static final String CLIENT_HELPER = "org.jenkinsci.plugins.p4.client.ClientHelper";
        private static final String CONNECTION_HELPER = "org.jenkinsci.plugins.p4.client.ConnectionHelper";
        private static final List<String> CONTEXT_TYPE_NAMES = List.of(
                "hudson.model.Item",
                "hudson.model.ItemGroup",
                "hudson.model.Run",
                "hudson.model.AbstractBuild",
                "hudson.model.AbstractProject",
                "hudson.model.Job"
        );
        private static final List<String> CONTEXT_NAME_HINTS = List.of("build", "project", "run", "item", "job", "context");

        @Override
        public void process(CtElement element) {
            if (element instanceof CtInvocation<?> invocation) {
                rewriteStringUtilsBreak(invocation);
            }
            if (element instanceof CtConstructorCall<?> constructorCall) {
                rewriteClientHelperConstructor(constructorCall);
            }
        }

        private void rewriteStringUtilsBreak(CtInvocation<?> invocation) {
            if (invocation.getExecutable() == null || invocation.getExecutable().getDeclaringType() == null) {
                return;
            }
            if (!STRING_UTILS.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())) {
                return;
            }
            if (!"isAllBlank".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            String args = invocation.getArguments().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(
                    "!" + STRING_UTILS + ".isAnyNotBlank(" + args + ")"));
        }

        private void rewriteClientHelperConstructor(CtConstructorCall<?> constructorCall) {
            if (constructorCall.getType() == null || !CLIENT_HELPER.equals(constructorCall.getType().getQualifiedName())) {
                return;
            }
            if (constructorCall.getArguments().size() != 4) {
                return;
            }

            String contextExpression = findContextExpression(constructorCall);
            String credentialsExpression = constructorCall.getArguments().get(0).toString();
            String listenerExpression = constructorCall.getArguments().get(1).toString();
            String replacement = "new " + CLIENT_HELPER + "(" + contextExpression + ", " + credentialsExpression +
                    ", " + listenerExpression + ", null)";
            constructorCall.replace(constructorCall.getFactory().Code().createCodeSnippetExpression(replacement));
        }

        private String findContextExpression(CtElement anchor) {
            CtType<?> enclosingType = anchor.getParent(CtType.class);
            if (enclosingType != null) {
                for (CtVariableAccess<?> access : enclosingType.getElements(new TypeFilter<>(CtVariableAccess.class))) {
                    String context = contextExpression(access);
                    if (context != null) {
                        return context;
                    }
                }

                for (CtExecutable<?> executable : enclosingType.getElements(new TypeFilter<>(CtExecutable.class))) {
                    for (CtVariableAccess<?> access : executable.getElements(new TypeFilter<>(CtVariableAccess.class))) {
                        String context = contextExpression(access);
                        if (context != null) {
                            return context;
                        }
                    }
                }
            }

            return "(hudson.model.ItemGroup) null";
        }

        private String contextExpression(CtVariableAccess<?> access) {
            if (access == null) {
                return null;
            }
            String name = access.getVariable() == null ? "" : access.getVariable().getSimpleName();
            CtTypeReference<?> type = access.getType();
            String qualifiedName = type == null ? null : type.getQualifiedName();

            if ("build".equals(name) || "run".equals(name) || "job".equals(name)) {
                return access + ".getParent()";
            }
            if (qualifiedName == null) {
                return null;
            }
            if (CONTEXT_TYPE_NAMES.contains(qualifiedName)) {
                if (qualifiedName.endsWith("AbstractBuild") || qualifiedName.endsWith("Run")) {
                    return access + ".getParent()";
                }
                return access.toString();
            }
            return null;
        }
    }
}
