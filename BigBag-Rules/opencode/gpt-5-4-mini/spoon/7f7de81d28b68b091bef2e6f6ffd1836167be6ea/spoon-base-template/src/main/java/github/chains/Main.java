package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

public class Main {
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args.length > 1 ? args[1] : args[0]);
        launcher.addProcessor(new RawManagedObjectProcessor());
        launcher.run();
    }

    private static final class RawManagedObjectProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private static final String SUB_REQUEST_TYPE = "org.snmp4j.agent.request.SubRequest";
        private static final String OBJECT_TYPE = "java.lang.Object";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> typeReference) {
            return TARGET_TYPE.equals(typeReference.getQualifiedName())
                    && typeReference.getActualTypeArguments().isEmpty()
                    && typeReference.getParent(CtImport.class) == null;
        }

        @Override
        public void process(CtTypeReference<?> typeReference) {
            CtWildcardReference objectWildcard = getFactory().createWildcardReference();
            objectWildcard.setUpper(true);
            objectWildcard.setBoundingType(getFactory().createReference(OBJECT_TYPE));

            CtTypeReference<?> subRequest = getFactory().createReference(SUB_REQUEST_TYPE);
            subRequest.addActualTypeArgument(objectWildcard);

            CtWildcardReference managedObjectWildcard = getFactory().createWildcardReference();
            managedObjectWildcard.setUpper(true);
            managedObjectWildcard.setBoundingType(subRequest);

            typeReference.addActualTypeArgument(managedObjectWildcard);
        }
    }
}
