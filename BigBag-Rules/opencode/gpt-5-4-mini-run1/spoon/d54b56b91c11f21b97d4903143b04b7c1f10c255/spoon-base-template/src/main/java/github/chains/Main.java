package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_PUBLISH_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_MESSAGE_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String SET_CONTEXT = "setContext";
    private static final String SINGLE_PARTITION_BUILDER =
            "com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder.Builder";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCopyResources(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new DependencyUpdateProcessor());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class DependencyUpdateProcessor extends AbstractProcessor<CtElement> {

        @Override
        public boolean isToBeProcessed(CtElement element) {
            return true;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtImport) {
                replacePublishMetadataImport((CtImport) element);
            }
            if (element instanceof CtTypeReference) {
                replacePublishMetadataReference((CtTypeReference<?>) element);
            }
            if (element instanceof CtInvocation) {
                removeLegacySetContextInvocation((CtInvocation<?>) element);
            }
        }

        private void replacePublishMetadataImport(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> reference = (CtTypeReference<?>) ctImport.getReference();
                if (OLD_PUBLISH_METADATA.equals(reference.getQualifiedName())) {
                    ctImport.setReference(ctImport.getFactory().Type().createReference(NEW_MESSAGE_METADATA));
                }
            }
        }

        private void replacePublishMetadataReference(CtTypeReference<?> reference) {
            if (OLD_PUBLISH_METADATA.equals(reference.getQualifiedName())) {
                reference.replace(reference.getFactory().Type().createReference(NEW_MESSAGE_METADATA));
            }
        }

        private void removeLegacySetContextInvocation(CtInvocation<?> invocation) {
            if (!SET_CONTEXT.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            if (invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget().clone());
                return;
            }
            invocation.delete();
        }
    }
}
