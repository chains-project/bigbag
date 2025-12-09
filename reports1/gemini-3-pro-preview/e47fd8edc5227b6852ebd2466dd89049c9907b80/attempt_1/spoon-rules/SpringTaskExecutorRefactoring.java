package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Processor for org.springframework.core.task.TaskExecutor.
 * 
 * ANALYSIS OF DIFF:
 * - CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED]
 * 
 * STRATEGY:
 * - Since the class is unchanged, binary compatible, and source compatible, 
 *   no AST transformation is required.
 * - This processor acts as a "Sanity Check" or "Scanner" to verify usages 
 *   of the class in the target codebase without modifying them.
 * - It strictly adheres to the SniperJavaPrettyPrinter configuration to ensure
 *   zero-noise output (preserving formatting/comments) for the unchanged code.
 */
public class SpringTaskExecutorRefactoring {

    public static class TaskExecutorSanityCheckProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath mode
            String qName = candidate.getQualifiedName();
            
            // 1. Check if the qualified name is resolvable
            if (qName == null || qName.isEmpty()) {
                return false;
            }

            // 2. Identify references to the specific TaskExecutor class
            // Using contains() to be robust against fully qualified vs imported simple names
            if (!qName.contains("org.springframework.core.task.TaskExecutor")) {
                return false;
            }

            // 3. Filter out the definition of the class itself if parsing the library source (optional safety)
            // But usually, we are processing client code, so this is fine.
            return true;
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            // LOGIC:
            // The diff explicitly states the class is UNCHANGED.
            // Therefore, we do NOT perform any replacement or modification.
            // We simply log the finding to confirm the tool is scanning correctly.
            
            String position = (ref.getPosition() != null && ref.getPosition().isValidPosition()) 
                ? "line " + ref.getPosition().getLine() 
                : "unknown location";

            System.out.println("[SanityCheck] Found usage of 'TaskExecutor' at " + position 
                + ". Status: UNCHANGED. No modification applied.");
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/e47fd8edc5227b6852ebd2466dd89049c9907b80/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e47fd8edc5227b6852ebd2466dd89049c9907b80/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e47fd8edc5227b6852ebd2466dd89049c9907b80/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e47fd8edc5227b6852ebd2466dd89049c9907b80/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually to preserve exact indentation and formatting
        //    This is crucial for "Unchanged" or minor refactorings to avoid noise.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new TaskExecutorSanityCheckProcessor());

        try {
            System.out.println("Starting Spring TaskExecutor analysis...");
            launcher.run();
            System.out.println("Analysis complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}