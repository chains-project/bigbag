package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class PubSubLiteRefactoring {

    /**
     * Processor to handle the removal of PublishMetadata by renaming references 
     * to its successor, MessageMetadata.
     */
    public static class PublishMetadataRenamer extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS = "com.google.cloud.pubsublite.PublishMetadata";
        private static final String NEW_SIMPLE_NAME = "MessageMetadata";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath: Check for nulls
            if (candidate == null) {
                return false;
            }

            // In NoClasspath, getQualifiedName() is generally reliable if imports are present.
            // We match strictly to avoid replacing unrelated classes with the same name.
            String qName = candidate.getQualifiedName();
            return qName != null && qName.equals(OLD_CLASS);
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            // Update the simple name.
            // Since both the old and new classes are in 'com.google.cloud.pubsublite',
            // changing the simple name is sufficient to update the Qualified Name effectively
            // and preserve the existing package structure in imports.
            ref.setSimpleName(NEW_SIMPLE_NAME);

            // Log the change
            String position = ref.getPosition().isValidPosition() 
                ? "line " + ref.getPosition().getLine() 
                : "unknown position";
            System.out.println("Refactored: PublishMetadata -> MessageMetadata at " + position);
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/88a20ece4db960e35fbfa39fcb40e61daceb15b1/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88a20ece4db960e35fbfa39fcb40e61daceb15b1/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/88a20ece4db960e35fbfa39fcb40e61daceb15b1/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88a20ece4db960e35fbfa39fcb40e61daceb15b1/attempt_1/transformed");

        // CRITICAL: Configure Environment for Robust Sniper Printing
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject SniperJavaPrettyPrinter to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PublishMetadataRenamer());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("An error occurred during refactoring:");
            e.printStackTrace();
        }
    }
}