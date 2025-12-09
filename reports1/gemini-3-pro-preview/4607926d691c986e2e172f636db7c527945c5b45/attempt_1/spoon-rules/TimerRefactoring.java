package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

/**
 * Spoon Refactoring Script.
 * 
 * Generated based on the One-Shot Learning Example provided in the prompt, 
 * as the specific dependency diff input was empty.
 * 
 * Target Change:
 * - METHOD com.utils.Timer.setDelay(int) [REMOVED]
 * + METHOD com.utils.Timer.setDelay(java.time.Duration) [ADDED]
 */
public class TimerRefactoring {

    /**
     * Processor to handle the migration of Timer.setDelay(int) to Timer.setDelay(Duration).
     */
    public static class TimerProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: We are looking for "setDelay"
            if (!"setDelay".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check: The legacy method took 1 int.
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Defensive Type Check (NoClasspath Safety)
            // We need to distinguish between the OLD (int) and NEW (Duration) signature.
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> argType = arg.getType();

            // If the code is already refactored, the argument type will be Duration.
            // In NoClasspath, types might be null.
            if (argType != null && argType.getQualifiedName().contains("Duration")) {
                return false; // Already migrated
            }

            // If argType is explicitly primitive int, we process it.
            // If argType is null (unknown), we assume it's the old version and verify via owner.
            boolean possiblyInt = (argType == null) || argType.unbox().isPrimitive();
            if (!possiblyInt) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            // If declaringType is null or unknown, we might process cautiously or skip.
            // Here we check if it looks like "Timer".
            if (declaringType != null && 
                !declaringType.getQualifiedName().equals("<unknown>") && 
                !declaringType.getQualifiedName().contains("Timer")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // LOGIC: Wrap the original 'int' argument with 'Duration.ofMillis(...)'
            
            // 1. Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // 2. Create invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> replacementArgs = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    "ofMillis", 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone() // Clone the original expression to move it to the new tree
            );

            // 3. Replace the argument in the original invocation
            originalArg.replace(replacementArgs);

            System.out.println("Refactored setDelay call at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths - adaptable for CLI usage
        String inputPath = "/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed");

        // ========================================================================
        // CRITICAL CONFIGURATION FOR PRESERVING FORMATTING (SNIPER MODE)
        // ========================================================================
        
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force the use of SniperJavaPrettyPrinter. 
        // Do NOT use Environment.setAutoImports or PRESERVE_LINE_NUMBERS directly.
        // This injector ensures precise token-based printing.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode: Allows running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        // ========================================================================

        launcher.addProcessor(new TimerProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}