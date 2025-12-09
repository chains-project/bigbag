package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Processor generated based on Dependency Diff.
 * 
 * Input Diff:
 * - CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED, binaryCompatible=true, sourceCompatible=true]
 * 
 * Analysis:
 * The provided diff indicates that 'org.springframework.core.task.TaskExecutor' has NOT changed
 * between versions (Binary and Source compatible).
 * 
 * Refactoring Strategy:
 * Since the status is UNCHANGED, no AST transformation (renaming, argument wrapping, etc.) is required.
 * This processor is implemented as a "Scanner" to identify existing usages of the class for auditing
 * purposes, but it deliberately skips any modification to preserve the source code exactly as is.
 */
public class TaskExecutorRefactoring {

    public static class TaskExecutorScanner extends AbstractProcessor<CtTypeReference<?>> {
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath mode
            if (candidate == null) return false;

            // 1. Match the class name
            // In NoClasspath, getQualifiedName() returns what is available from source.
            // We look for the fully qualified name if imports resolve, or a strong heuristic.
            String qName = candidate.getQualifiedName();
            
            // Check if it matches the target class
            if (qName != null && qName.contains("org.springframework.core.task.TaskExecutor")) {
                return true;
            }
            
            return false;
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // LOGIC: No Transformation.
            // The diff explicitly states the class is UNCHANGED.
            // We only log the detection to verify the processor is running correctly.
            
            String position = candidate.getPosition().isValidPosition() 
                ? "line " + candidate.getPosition().getLine() 
                : "unknown position";
                
            System.out.println("[Audit] Found unchanged TaskExecutor usage at " + position);
            
            // No candidate.replace(...) calls are made here.
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or passed as args in a real CLI)
        String inputPath = "/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed");

        // =======================================================================
        // CRITICAL IMPLEMENTATION RULES (Sniper & NoClasspath)
        // =======================================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation strictly
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // =======================================================================
        
        launcher.addProcessor(new TaskExecutorScanner());

        try {
            System.out.println("Starting Spoon processing...");
            launcher.run();
            System.out.println("Processing complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}