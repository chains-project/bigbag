package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String INPUT_DIR = "src";
    private static final String TARGET_TYPE = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String TARGET_METHOD = "getInstance";
    private static final String TARGET_FIELD = "INSTANCE";

    public static void main(String[] args) {
        String inputDir = args.length > 0 ? args[0] : INPUT_DIR;
        String outputDir = args.length > 1 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(Path.of(outputDir).toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setShouldCompile(false);

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public boolean isToBeProcessed(CtInvocation<?> invocation) {
                CtExecutableReference<?> executable = invocation.getExecutable();
                CtTypeReference<?> declaringType = executable.getDeclaringType();
                return executable != null
                        && TARGET_METHOD.equals(executable.getSimpleName())
                        && executable.getParameters().isEmpty()
                        && declaringType != null
                        && TARGET_TYPE.equals(declaringType.getQualifiedName());
            }

            @Override
            public void process(CtInvocation<?> invocation) {
                CtTypeReference<?> targetType = invocation.getFactory().Type().createReference(TARGET_TYPE);
                @SuppressWarnings({"rawtypes", "unchecked"})
                CtFieldReference fieldReference = invocation.getFactory().Field().createReference((CtTypeReference) targetType, (CtTypeReference) targetType, TARGET_FIELD);
                @SuppressWarnings({"rawtypes", "unchecked"})
                CtFieldRead replacement = invocation.getFactory().Core().createFieldRead();
                replacement.setVariable(fieldReference);
                replacement.setTarget(invocation.getFactory().Code().createTypeAccess(targetType));
                invocation.replace(replacement);
            }
        });

        launcher.run();
    }
}
