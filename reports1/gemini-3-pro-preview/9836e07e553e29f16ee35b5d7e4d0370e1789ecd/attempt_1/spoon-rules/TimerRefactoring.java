package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Refactoring Script generated for:
 * - METHOD com.utils.Timer.setDelay(int) [REMOVED]
 * + METHOD com.utils.Timer.setDelay(java.time.Duration) [ADDED]
 * 
 * Note: No specific diff was provided in the input block. 
 * This code implements the migration logic for the example scenario provided in the system prompt.
 */
public class TimerRefactoring {

    /**
     * Processor to migrate Timer.setDelay(int) to Timer.setDelay(Duration.ofMillis(int)).
     */
    public static class TimerProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"setDelay".equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            // We use simple string matching on the qualified name to avoid resolution errors.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && !declaringType.getQualifiedName().contains("Timer")) {
                // If we are sure it's NOT a Timer (and not unknown), skip it.
                // If it's unknown/null, we proceed cautiously.
                return false;
            }

            // 4. Check Argument Type
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> type = arg.getType();

            // If the type is explicitly known to be 'Duration', it's already migrated.
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }

            // If type is null (NoClasspath cannot resolve) or is primitive int, we process it.
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // Create invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> wrappingInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef,
                    durationRef, // return type
                    "ofMillis",
                    factory.Type().integerPrimitiveType() // param type
                ),
                originalArg.clone() // Clone the original argument to move it
            );

            // Replace the original argument with the wrapped invocation
            originalArg.replace(wrappingInvocation);

            System.out.println("Refactored Timer.setDelay at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().toString() : "unknown position"));
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR SOURCE PRESERVATION ---
        
        // 1. Enable NoClasspath mode (Defensive coding assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // 2. Preserve Comments
        launcher.getEnvironment().setCommentEnabled(true);

        // 3. Force SniperJavaPrettyPrinter for high-fidelity source reproduction
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // --- ADD PROCESSOR ---
        launcher.addProcessor(new TimerProcessor());

        System.out.println("Starting refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}