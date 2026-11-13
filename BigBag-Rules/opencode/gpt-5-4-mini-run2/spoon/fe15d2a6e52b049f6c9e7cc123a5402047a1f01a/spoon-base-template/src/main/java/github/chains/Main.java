package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String TARGET_TYPE = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String TARGET_METHOD = "getInstance";
    private static final String TARGET_FIELD = "INSTANCE";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-transformed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(inputDir);
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!isTargetInvocation(invocation)) {
                    return;
                }

                CtTypeReference<?> ownerType = getOwnerType(invocation);
                if (ownerType == null) {
                    return;
                }

                CtFieldRead<?> replacement = createStaticFieldRead(launcher, ownerType);
                invocation.replace(replacement);
            }
        });

        launcher.run();
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        if (invocation.getArguments() != null && !invocation.getArguments().isEmpty()) {
            return false;
        }

        if (invocation.getExecutable() == null || !TARGET_METHOD.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        if (invocation.getExecutable().getDeclaringType() != null
                && TARGET_TYPE.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())) {
            return true;
        }

        return invocation.getTarget() instanceof CtTypeAccess
                && TARGET_TYPE.equals(((CtTypeAccess<?>) invocation.getTarget()).getAccessedType().getQualifiedName());
    }

    private static CtTypeReference<?> getOwnerType(CtInvocation<?> invocation) {
        if (invocation.getExecutable() != null && invocation.getExecutable().getDeclaringType() != null) {
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (TARGET_TYPE.equals(declaringType.getQualifiedName())) {
                return declaringType;
            }
        }

        if (invocation.getTarget() instanceof CtTypeAccess) {
            CtTypeReference<?> accessedType = ((CtTypeAccess<?>) invocation.getTarget()).getAccessedType();
            if (accessedType != null && TARGET_TYPE.equals(accessedType.getQualifiedName())) {
                return accessedType;
            }
        }

        return null;
    }

    private static CtFieldRead<?> createStaticFieldRead(Launcher launcher, CtTypeReference<?> ownerType) {
        CtTypeReference<?> ownerTypeRef = ownerType.clone();
        CtFieldReference<?> fieldReference = launcher.getFactory().Field().createReference(ownerTypeRef, ownerTypeRef.clone(), TARGET_FIELD);
        fieldReference.setStatic(true);

        @SuppressWarnings({"rawtypes", "unchecked"})
        CtFieldRead fieldRead = launcher.getFactory().createFieldRead();
        fieldRead.setVariable(fieldReference);
        fieldRead.setTarget(launcher.getFactory().createTypeAccess(ownerType.clone()));
        return (CtFieldRead<?>) fieldRead;
    }
}
