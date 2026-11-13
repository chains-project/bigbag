package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OBJECT_MAPPER_FQN = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String POLYMORPHIC_VALIDATOR_EXPR =
            "com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!isJacksonDefaultTypingCall(invocation)) {
                continue;
            }

            String replacement = rewriteDefaultTypingCall(invocation);
            if (replacement != null) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(replacement));
            }
        }

        launcher.prettyprint();
    }

    private static boolean isJacksonDefaultTypingCall(CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null || invocation.getExecutable().getDeclaringType() == null) {
            return false;
        }

        if (!OBJECT_MAPPER_FQN.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())) {
            return false;
        }

        String methodName = invocation.getExecutable().getSimpleName();
        return "enableDefaultTyping".equals(methodName) || "enableDefaultTypingAsProperty".equals(methodName);
    }

    private static String rewriteDefaultTypingCall(CtInvocation<?> invocation) {
        String target = invocation.getTarget() == null ? null : invocation.getTarget().toString();
        if (target == null || target.isBlank()) {
            return null;
        }

        String args = invocation.getArguments().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        String methodName = invocation.getExecutable().getSimpleName();
        if ("enableDefaultTyping".equals(methodName)) {
            if (invocation.getArguments().isEmpty()) {
                return target + ".activateDefaultTyping(" + POLYMORPHIC_VALIDATOR_EXPR + ")";
            }
            return target + ".activateDefaultTyping(" + POLYMORPHIC_VALIDATOR_EXPR + ", " + args + ")";
        }

        if ("enableDefaultTypingAsProperty".equals(methodName)) {
            return target + ".activateDefaultTypingAsProperty(" + POLYMORPHIC_VALIDATOR_EXPR + ", " + args + ")";
        }

        return null;
    }
}
