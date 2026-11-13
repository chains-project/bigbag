package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String INPUT_TYPE = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String INPUT_METHOD = "getInstance";
    private static final String OUTPUT_FIELD = "INSTANCE";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new ReplaceJaxbToStringStrategyGetInstance());
        launcher.buildModel();
        launcher.prettyprint();
    }

    private static final class ReplaceJaxbToStringStrategyGetInstance extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!INPUT_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null || !INPUT_TYPE.equals(declaringType.getQualifiedName())) {
                return;
            }

            CtFieldRead<Object> replacement = createFieldRead(invocation.getFactory());
            invocation.replace(replacement);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CtFieldRead<Object> createFieldRead(Factory factory) {
            CtTypeReference typeReference = factory.Type().createReference(INPUT_TYPE);
            CtFieldReference fieldReference = factory.Core().createFieldReference();
            fieldReference.setDeclaringType(typeReference);
            fieldReference.setSimpleName(OUTPUT_FIELD);

            CtFieldRead<Object> fieldRead = factory.Core().createFieldRead();
            fieldRead.setVariable(fieldReference);
            fieldRead.setTarget(factory.Code().createTypeAccess(typeReference));
            return fieldRead;
        }
    }
}
