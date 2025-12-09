package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class TaskExecutorRefactoring {

    /**
     * Processor based on the dependency diff analysis.
     * 
     * Diff Analysis:
     * - CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED]
     * 
     * Strategy:
     * Since the class is explicitly marked as UNCHANGED, binary compatible, and source compatible,
     * no structural modifications (renaming, method changes, etc.) are required.
     * 
     * This processor acts as a scanner to verify usages of the class exist, but performs no mutations
     * to preserve the source code exactly as is.
     */
    public static class TaskExecutorProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding: Check for null
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }
            
            // Match the specific class mentioned in the diff
            return "org.springframework.core.task.TaskExecutor".equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Logic:
            // The diff indicates "UNCHANGED". 
            // Therefore, we do not modify the AST.
            // We simply log detection for verification purposes.
            System.out.println("Detected valid usage of TaskExecutor (No changes needed) at: " 
                + (candidate.getPosition().isValidPosition() ? candidate.getPosition().toString() : "Unknown Position"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1e1de78344a89be66d2e78f7adb07a479f6677eb/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1e1de78344a89be66d2e78f7adb07a479f6677eb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1e1de78344a89be66d2e78f7adb07a479f6677eb/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1e1de78344a89be66d2e78f7adb07a479f6677eb/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for robust preservation of unrelated code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: Run without classpath to handle incomplete environments
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TaskExecutorProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}