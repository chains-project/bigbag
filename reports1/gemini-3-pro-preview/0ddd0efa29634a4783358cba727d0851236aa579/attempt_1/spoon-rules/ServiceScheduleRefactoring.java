package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ServiceScheduleRefactoring {

    /**
     * Processor to migrate com.legacy.Service.schedule(Date) -> schedule(Instant).
     */
    public static class ScheduleProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"schedule".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // If we know it's already an Instant, skip it.
            // If it's null (unknown type in NoClasspath) or explicitly Date, we process it.
            if (type != null && type.getQualifiedName().contains("java.time.Instant")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Service") && !owner.getQualifiedName().equals("<unknown>")) {
                // If we are sure it belongs to a class NOT named Service, skip.
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation: Convert originalArg (Date) -> originalArg.toInstant()
            // We create: invocation( target=originalArg, method="toInstant" )
            
            // Note: In NoClasspath, we often construct the method reference manually 
            // because we can't look up java.util.Date reflectively safely.
            CtTypeReference<?> dateType = factory.Type().createReference("java.util.Date");
            CtTypeReference<?> instantType = factory.Type().createReference("java.time.Instant");

            CtInvocation<?> toInstantCall = factory.Code().createInvocation(
                originalArg.clone(), // Target is the original argument (the Date object)
                factory.Method().createReference(dateType, instantType, "toInstant") // Method ref
            );

            // Replace the argument in the original invocation
            originalArg.replace(toInstantCall);
            
            System.out.println("Refactored Service.schedule at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed");

        // CRITICAL SETTINGS FOR SPOON 11+
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new ScheduleProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}