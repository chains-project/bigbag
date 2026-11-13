package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String JACKSON_OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String JACKSON_VALIDATOR = "com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args.length > 1 ? args[1] : args[0]));
        launcher.addProcessor(new JacksonDefaultTypingProcessor());
        launcher.run();
    }

    private static final class JacksonDefaultTypingProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || executable.getDeclaringType() == null) {
                return;
            }

            if (!JACKSON_OBJECT_MAPPER.equals(executable.getDeclaringType().getQualifiedName())) {
                return;
            }

            String methodName = executable.getSimpleName();
            String replacement = switch (methodName) {
                case "enableDefaultTyping" -> rewriteEnableDefaultTyping(invocation.getArguments(), invocation.getTarget() == null ? "" : invocation.getTarget() + ".");
                case "enableDefaultTypingAsProperty" -> rewriteEnableDefaultTypingAsProperty(invocation.getArguments(), invocation.getTarget() == null ? "" : invocation.getTarget() + ".");
                case "disableDefaultTyping" -> rewriteDisableDefaultTyping(invocation.getTarget() == null ? "" : invocation.getTarget() + ".");
                default -> null;
            };

            if (replacement != null) {
                invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(replacement));
            }
        }

        private String rewriteEnableDefaultTyping(List<?> arguments, String targetPrefix) {
            if (arguments.isEmpty()) {
                return targetPrefix + "activateDefaultTyping(" + JACKSON_VALIDATOR + ")";
            }
            if (arguments.size() == 1) {
                return targetPrefix + "activateDefaultTyping(" + JACKSON_VALIDATOR + ", " + arguments.get(0) + ")";
            }
            return targetPrefix + "activateDefaultTyping(" + JACKSON_VALIDATOR + ", " + arguments.get(0) + ", " + arguments.get(1) + ")";
        }

        private String rewriteEnableDefaultTypingAsProperty(List<?> arguments, String targetPrefix) {
            if (arguments.size() < 2) {
                return null;
            }
            return targetPrefix + "activateDefaultTypingAsProperty(" + JACKSON_VALIDATOR + ", " + arguments.get(0) + ", " + arguments.get(1) + ")";
        }

        private String rewriteDisableDefaultTyping(String targetPrefix) {
            return targetPrefix + "deactivateDefaultTyping()";
        }
    }
}
