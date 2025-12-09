package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class PubNubRefactoring {

    /**
     * Processor to handle the breaking change in PNConfiguration constructor.
     * <p>
     * Change:
     * - REMOVED: PNConfiguration()
     * - ADDED: PNConfiguration(UserId)
     * <p>
     * Strategy:
     * Replace `new PNConfiguration()` with `new PNConfiguration(new UserId("replace_with_your_user_id"))`.
     * This ensures the code compiles, though the user must provide a valid User ID string.
     */
    public static class PNConfigurationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check if the type is PNConfiguration
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;
            
            // Use relaxed name checking for NoClasspath compatibility
            if (!"PNConfiguration".equals(type.getSimpleName()) && !type.getQualifiedName().endsWith("PNConfiguration")) {
                return false;
            }

            // 2. Check if it is the no-argument constructor
            // We only want to transform `new PNConfiguration()`
            return candidate.getArguments().isEmpty();
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            Factory factory = getFactory();
            
            // 1. Create a reference to the new required type: com.pubnub.api.UserId
            CtTypeReference<?> userIdTypeRef = factory.Type().createReference("com.pubnub.api.UserId");
            
            // 2. Create the argument for UserId constructor (a placeholder string)
            CtExpression<String> placeholderId = factory.Code().createLiteral("replace_with_your_user_id");
            
            // 3. Create the `new UserId("...")` expression
            CtConstructorCall<?> userIdConstructorCall = factory.Code().createConstructorCall(userIdTypeRef, placeholderId);
            
            // 4. Inject this new expression as an argument to the PNConfiguration constructor
            candidate.addArgument(userIdConstructorCall);
            
            System.out.println("Refactored PNConfiguration constructor at line " + candidate.getPosition().getLine() 
                + ". Please update the placeholder user ID.");
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args if expanded)
        String inputPath = "/home/kth/Documents/last_transformer/output/f714a41f0ffe9720939d4980da5119d1f45bd770/XChange/xchange-stream-service-pubnub/src/main/java/info/bitrich/xchangestream/service/pubnub/PubnubStreamingService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f714a41f0ffe9720939d4980da5119d1f45bd770/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f714a41f0ffe9720939d4980da5119d1f45bd770/XChange/xchange-stream-service-pubnub/src/main/java/info/bitrich/xchangestream/service/pubnub/PubnubStreamingService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f714a41f0ffe9720939d4980da5119d1f45bd770/attempt_1/transformed");

        // CRITICAL: Configure Environment for Non-Destructive Refactoring
        // 1. Enable comment preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Use SniperJavaPrettyPrinter to preserve original formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PNConfigurationProcessor());

        try {
            System.out.println("Starting PubNub Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}