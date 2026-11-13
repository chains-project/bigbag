package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_WRAPPER_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_GETTER = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <inputDir> <outputDir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public boolean isToBeProcessed(CtInvocation<?> invocation) {
                CtExecutableReference<?> executable = invocation.getExecutable();
                CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
                return declaringType != null
                        && OLD_WRAPPER_TYPE.equals(declaringType.getQualifiedName())
                        && OLD_GETTER.equals(executable.getSimpleName())
                        && invocation.getTarget() instanceof CtConstructorCall;
            }

            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public void process(CtInvocation<?> invocation) {
                CtConstructorCall constructorCall = (CtConstructorCall) invocation.getTarget();
                if (!constructorCall.getArguments().isEmpty()) {
                    CtExpression replacement = (CtExpression) constructorCall.getArguments().get(0);
                    invocation.replace(replacement);
                }
            }
        });

        launcher.run();
    }
}
