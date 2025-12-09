package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class PubSubLiteRefactoring {

    /**
     * Processor to handle breaking changes in Google Cloud Pub/Sub Lite.
     * 
     * Changes handled:
     * 1. Renaming: com.google.cloud.pubsublite.PublishMetadata -> com.google.cloud.pubsublite.MessageMetadata
     *    (Includes static factory method .of(...))
     * 2. Removal: setContext(PubsubContext) removed from internal Builders.
     */
    public static class PubSubLiteProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // Filter 1: Type References for PublishMetadata
            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
                return "com.google.cloud.pubsublite.PublishMetadata".equals(ref.getQualifiedName());
            }
            
            // Filter 2: Invocations of setContext
            if (candidate instanceof CtInvocation) {
                CtInvocation<?> inv = (CtInvocation<?>) candidate;
                // Only target methods named "setContext"
                return "setContext".equals(inv.getExecutable().getSimpleName());
            }
            
            return false;
        }

        @Override
        public void process(CtElement candidate) {
            if (candidate instanceof CtTypeReference) {
                processTypeRename((CtTypeReference<?>) candidate);
            } else if (candidate instanceof CtInvocation) {
                processSetContextRemoval((CtInvocation<?>) candidate);
            }
        }

        /**
         * Refactors usage of PublishMetadata to MessageMetadata.
         * PublishMetadata was removed and replaced by MessageMetadata with a compatible API.
         */
        private void processTypeRename(CtTypeReference<?> ref) {
            // Defensive check to avoid double processing
            if ("MessageMetadata".equals(ref.getSimpleName())) return;

            // Change the name. This updates variable types, return types, and static access.
            // e.g., "PublishMetadata var" -> "MessageMetadata var"
            // e.g., "PublishMetadata.of(...)" -> "MessageMetadata.of(...)"
            ref.setSimpleName("MessageMetadata");
            
            // We do not change the package because MessageMetadata resides in the same package 
            // (com.google.cloud.pubsublite) as the removed class.
        }

        /**
         * Removes calls to .setContext(PubsubContext) which were removed from Publisher/Subscriber builders.
         */
        private void processSetContextRemoval(CtInvocation<?> inv) {
            // 1. Argument Count Check
            List<CtExpression<?>> args = inv.getArguments();
            if (args.size() != 1) return;

            // 2. Argument Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> argType = arg.getType();

            // If we can resolve the type, and it is DEFINITELY NOT PubsubContext, we skip.
            // If type is null (unresolved), we assume it might be our target and proceed 
            // (risky but necessary for NoClasspath, mitigated by method name check).
            if (argType != null && !argType.getQualifiedName().contains("PubsubContext")) {
                return;
            }

            // 3. Transformation: Replace the invocation with its target (receiver).
            // Pattern: builder.setContext(ctx) -> builder
            CtExpression<?> target = inv.getTarget();
            
            if (target != null) {
                // If part of a fluent chain: x.setContext(c).build() -> x.build()
                // If standalone statement: x.setContext(c); -> x; (valid statement)
                inv.replace(target);
                System.out.println("Refactored: Removed setContext(...) at line " + 
                    (inv.getPosition().isValidPosition() ? inv.getPosition().getLine() : "unknown"));
            } else {
                // Unlikely for this specific API, but if no target exists, just delete.
                inv.delete();
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/d54b56b91c11f21b97d4903143b04b7c1f10c255/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactoryImpl.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d54b56b91c11f21b97d4903143b04b7c1f10c255/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d54b56b91c11f21b97d4903143b04b7c1f10c255/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactoryImpl.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d54b56b91c11f21b97d4903143b04b7c1f10c255/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (analyzes source without binary dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new PubSubLiteProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}