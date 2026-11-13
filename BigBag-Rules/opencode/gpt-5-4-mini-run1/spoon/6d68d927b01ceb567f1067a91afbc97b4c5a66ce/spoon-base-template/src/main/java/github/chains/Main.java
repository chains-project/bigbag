package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVA_SCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Path.of(args[0]);
        Path output = args.length > 1 ? Path.of(args[1]) : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                CtExpression<?> target = invocation.getTarget();
                if (!(target instanceof CtConstructorCall<?> constructorCall)) {
                    return;
                }

                if (!isOldScriptResultConstructor(constructorCall)) {
                    return;
                }

                if (!GET_JAVA_SCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())) {
                    return;
                }

                if (constructorCall.getArguments().isEmpty()) {
                    return;
                }

                CtElement replacement = constructorCall.getArguments().get(0).clone();
                invocation.replace(replacement);
            }

            private boolean isOldScriptResultConstructor(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> type = constructorCall.getType();
                return type != null && OLD_SCRIPT_RESULT_FQN.equals(type.getQualifiedName());
            }
        });

        launcher.run();
    }
}
