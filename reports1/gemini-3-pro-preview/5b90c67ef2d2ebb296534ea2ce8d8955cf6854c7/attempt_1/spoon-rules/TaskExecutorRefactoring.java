package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring/Analysis script for org.springframework.core.task.TaskExecutor.
 * 
 * Based on the diff: [status=UNCHANGED].
 * This processor scans for usages to ensure binary compatibility but strictly performs
 * no mutations as the API is stable.
 */
public class TaskExecutorRefactoring {

    /**
     * Processor to identify usages of TaskExecutor.
     * Uses CtTypeReference to catch fields, parameters, return types, and local variables.
     */
    public static class TaskExecutorProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Defensive Null Check
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // 2. Name Match
            // using .contains() for relaxed matching in NoClasspath mode
            // We target the specific Spring interface
            return candidate.getQualifiedName().contains("org.springframework.core.task.TaskExecutor");
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            // 3. Logic: Audit / Log
            // Since the diff indicates the class is UNCHANGED, we do not mutate the code.
            // This serves to verify the AST can resolve these references correctly.
            
            String position = "unknown line";
            if (typeRef.getPosition().isValidPosition()) {
                position = "line " + typeRef.getPosition().getLine();
            }

            System.out.println("[Audit] Verified TaskExecutor usage at " + position + 
                             " in " + typeRef.getParent().getClass().getSimpleName());
            
            // Example Placeholder for future mutations:
            // if we needed to rename it, we would use: typeRef.setSimpleName("NewExecutorName");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/attempt_1/transformed");

        // =========================================================================
        // CRITICAL IMPLEMENTATION RULES (Do not violate)
        // =========================================================================

        // 1. Enable comments to preserve file structure
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);

        // =========================================================================

        // Register the processor
        launcher.addProcessor(new TaskExecutorProcessor());

        try {
            System.out.println("Starting TaskExecutor analysis...");
            launcher.run();
            System.out.println("Analysis complete. Check console for detected usages.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}