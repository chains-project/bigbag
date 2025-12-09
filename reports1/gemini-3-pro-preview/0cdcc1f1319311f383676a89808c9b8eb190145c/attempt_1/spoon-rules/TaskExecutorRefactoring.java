package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring/Analysis Rule for org.springframework.core.task.TaskExecutor.
 * 
 * ANALYSIS:
 * Input Diff: CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED]
 * Strategy: The diff indicates no API breaking changes. 
 *           This processor identifies usages of the class for verification purposes
 *           but performs NO transformations, preserving source code integrity.
 */
public class TaskExecutorRefactoring {

    public static class TaskExecutorProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Defensive Check for NoClasspath
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // 2. Match the specific class
            // Using string matching to be safe in NoClasspath mode where reflection might fail
            String qName = candidate.getQualifiedName();
            if (qName.equals("org.springframework.core.task.TaskExecutor")) {
                return true;
            }
            
            // Handle cases where it might be simple name "TaskExecutor" 
            // and we rely on imports (approximated in NoClasspath)
            return "TaskExecutor".equals(candidate.getSimpleName()) && 
                   (qName.contains("springframework") || qName.equals("TaskExecutor"));
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Logic: The diff states the class is UNCHANGED.
            // Action: Log presence, do not modify.
            
            // Note: If a transformation were needed in the future, 
            // typeRef.replace(...) would be called here.
            
            System.out.println("Verified usage of stable API: " + 
                typeRef.getQualifiedName() + " at line " + 
                (typeRef.getPosition().isValidPosition() ? typeRef.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed");

        // ========================================================================
        // CRITICAL CONFIGURATION: PRESERVE FORMATTING
        // ========================================================================
        // 1. Enable comments to ensure they are captured in the AST
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to allow precise modification (or no-op)
        //    without destroying original formatting, indentation, or imports.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);
        // ========================================================================

        launcher.addProcessor(new TaskExecutorProcessor());

        try {
            System.out.println("Starting Analysis/Refactoring for TaskExecutor...");
            launcher.run();
            System.out.println("Processing complete. Check " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}