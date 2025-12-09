package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Processor for org.springframework.core.task.TaskExecutor.
 * 
 * ANALYSIS:
 * The provided diff indicates:
 * - CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED]
 * 
 * STRATEGY:
 * Since the class is binary and source compatible with no changes, 
 * this processor acts as a "Pass-Through" or "Audit" tool.
 * It detects usages of TaskExecutor to verify connectivity but performs 
 * NO AST mutations, preserving the source code exactly as is.
 */
public class TaskExecutorRefactoring {

    public static class TaskExecutorAuditProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive Coding: Handle nulls for NoClasspath environments
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // Match against the specific class in the diff
            // We use string matching to be safe in NoClasspath mode
            return candidate.getQualifiedName().equals("org.springframework.core.task.TaskExecutor");
        }

        @Override
        public void process(CtTypeReference<?> element) {
            // Since the diff states [status=UNCHANGED], we do not modify the AST.
            // We simply log the finding for audit purposes.
            // If refactoring were needed later (e.g., renaming), it would happen here.
            
            String position = (element.getPosition().isValidPosition()) 
                ? "line " + element.getPosition().getLine() 
                : "unknown position";
                
            System.out.println("[Audit] Found usage of valid TaskExecutor at " + position);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ab70529b2edf0a0b3f672278e191dc207d1b8711/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab70529b2edf0a0b3f672278e191dc207d1b8711/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ab70529b2edf0a0b3f672278e191dc207d1b8711/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab70529b2edf0a0b3f672278e191dc207d1b8711/attempt_1/transformed");

        // =========================================================
        // CRITICAL IMPLEMENTATION RULES: PRESERVE SOURCE CODE
        // =========================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Defensive Coding)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new TaskExecutorAuditProcessor());

        try {
            System.out.println("Starting Spoon Processing...");
            launcher.run();
            System.out.println("Processing complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during Spoon processing:");
            e.printStackTrace();
        }
    }
}