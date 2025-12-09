package org.example.refactoring;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Automates refactoring for API changes where an integer argument (milliseconds)
 * is replaced by a java.time.Duration object.
 * 
 * Example:
 * client.setTimeout(5000); -> client.setTimeout(java.time.Duration.ofMillis(5000));
 */
public class HttpClientRefactoring {

    public static class TimeoutProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_METHOD_NAME = "setTimeout";
        private static final String TARGET_OWNER_MATCH = "HttpClient";
        private static final String WRAPPER_CLASS = "java.time.Duration";
        private static final String WRAPPER_FACTORY_METHOD = "ofMillis";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Defensive Type Check (NoClasspath Compatibility)
            // We must handle cases where types cannot be resolved (null).
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // If we can resolve the type, and it is ALREADY the new type, skip it.
            if (argType != null && argType.getQualifiedName().contains(WRAPPER_CLASS)) {
                return false;
            }

            // 4. Owner Check (Relaxed String Matching)
            // In NoClasspath, the declaring type might be null or <unknown>.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null 
                && !declaringType.getQualifiedName().equals("<unknown>")
                && !declaringType.getQualifiedName().contains(TARGET_OWNER_MATCH)) {
                return false;
            }

            // If ambiguous (null owner), we err on the side of caution and process it
            // if the method name matches, or add stricter logic here if specific context is known.
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation Logic: Wrap the original argument
            // Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference(WRAPPER_CLASS);

            // Create invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    WRAPPER_FACTORY_METHOD, 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone() // Clone to detach from current parent
            );

            // Apply replacement
            originalArg.replace(replacement);
            
            System.out.println("[Refactoring] Updated " + TARGET_METHOD_NAME + 
                " at " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Configuration paths - adjust as needed
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // =========================================================
        // CRITICAL: SNIPER MODE CONFIGURATION
        // =========================================================
        // 1. Preserve comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to preserve original formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Set NoClasspath to true (defensive mode)
        launcher.getEnvironment().setNoClasspath(true);
        // =========================================================

        launcher.addProcessor(new TimeoutProcessor());

        try {
            System.out.println("Starting Refactoring via Spoon...");
            launcher.run();
            System.out.println("Refactoring complete. Check output at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}