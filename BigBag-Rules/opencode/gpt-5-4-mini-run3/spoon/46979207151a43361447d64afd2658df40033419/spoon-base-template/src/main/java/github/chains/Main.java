package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

import java.io.File;

public class Main {
    private static final String DEPENDENCY_TYPE = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String REPLACEMENT_FIELD = "INSTANCE";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory as the first argument");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!isDeprecatedGetterCall(invocation)) {
                    return;
                }

                CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
                if (declaringType == null || !DEPENDENCY_TYPE.equals(declaringType.getQualifiedName())) {
                    return;
                }

                CtFieldRead replacement = getFactory().Core().createFieldRead();
                replacement.setVariable((CtVariableReference) getFactory().Field().createReference(
                        declaringType,
                        declaringType,
                        REPLACEMENT_FIELD));
                invocation.replace(replacement);
            }
        });
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static boolean isDeprecatedGetterCall(CtInvocation<?> invocation) {
        return invocation.getExecutable() != null
                && "getInstance".equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().isEmpty();
    }
}
