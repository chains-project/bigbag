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
 * Generated based on the logic:
 * OLD: com.utils.Timer.setDelay(int)
 * NEW: com.utils.Timer.setDelay(java.time.Duration)
 * 
 * Strategy: Wrap the integer argument in java.time.Duration.ofMillis(...)
 */
public class TimerRefactoring {

    public static class TimerProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setDelay".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> type = arg.getType();

            // If we can resolve the type and it is already Duration, we skip it.
            // If type is null (NoClasspath) or primitive (int), we assume it needs refactoring.
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath safety)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // If owner is known and does not contain "Timer", skip. 
            // If owner is unknown (null) or "<unknown>", we act conservatively and process it 
            // (assuming the method name/arg count uniqueness is sufficient).
            if (owner != null && !owner.getQualifiedName().contains("Timer") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Create reference for java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // Create the wrapping invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    "ofMillis", 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone()
            );

            // Replace the original argument with the wrapped version
            originalArg.replace(replacement);
            
            System.out.println("Refactored setDelay at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths - adjust as necessary
        String inputPath = "/home/kth/Documents/last_transformer/output/41ec14e7e0ccf28476905eb28b2155b11d8a55f5/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/view/CrudifierView.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/41ec14e7e0ccf28476905eb28b2155b11d8a55f5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/41ec14e7e0ccf28476905eb28b2155b11d8a55f5/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/view/CrudifierView.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/41ec14e7e0ccf28476905eb28b2155b11d8a55f5/attempt_1/transformed");

        // CRITICAL: Configure Environment for Robust Sniper Printing
        // 1. Preserve comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new TimerProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}