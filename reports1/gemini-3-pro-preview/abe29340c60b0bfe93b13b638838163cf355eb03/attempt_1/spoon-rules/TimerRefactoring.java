package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Transformation Rule.
 * Generated for: setDelay(int) -> setDelay(Duration.ofMillis(int))
 * 
 * Rules applied:
 * 1. Strict source preservation using SniperJavaPrettyPrinter.
 * 2. Defensive coding for NoClasspath environment.
 * 3. Wildcard generics for type safety.
 */
public class TimerRefactoring {

    public static class TimerProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We are looking for the method 'setDelay'
            if (!"setDelay".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // The old signature took exactly one integer argument
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // If we can resolve the type and it is already 'Duration', it's likely already migrated.
            // In NoClasspath, getType() might be null, so we must handle that.
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching)
            // Ensure we are modifying the correct class (Timer), but handle unknown types gracefully.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Timer") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Create a reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // Construct the replacement: Duration.ofMillis(originalArg)
            // Note: ofMillis takes a long, but int is compatible.
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    "ofMillis", 
                    factory.Type().longPrimitiveType()
                ),
                originalArg.clone() // Clone the argument to safe-guard tree integrity
            );

            // Replace the original argument with the new wrapped invocation
            originalArg.replace(replacement);
            
            System.out.println("Refactored setDelay at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or parameterized)
        String inputPath = "/home/kth/Documents/last_transformer/output/abe29340c60b0bfe93b13b638838163cf355eb03/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/abe29340c60b0bfe93b13b638838163cf355eb03/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/abe29340c60b0bfe93b13b638838163cf355eb03/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/abe29340c60b0bfe93b13b638838163cf355eb03/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---

        // 1. Enable comments to preserve them in the output
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually for strict source preservation (indentation, whitespace)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Compatibility (Assume libraries are missing)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new TimerProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output written to " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}