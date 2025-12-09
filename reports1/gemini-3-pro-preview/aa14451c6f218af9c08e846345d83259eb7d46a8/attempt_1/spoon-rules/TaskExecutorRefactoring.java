package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule based on dependency diff analysis.
 *
 * Input Diff:
 * - CLASS org.springframework.core.task.TaskExecutor [status=UNCHANGED, binaryCompatible=true, sourceCompatible=true]
 *
 * Analysis:
 * The diff explicitly states that 'TaskExecutor' is UNCHANGED and remains binary/source compatible.
 * Therefore, no AST transformation is required.
 *
 * This class provides the necessary Spoon boilerplate and a processor that identifies
 * usages of the class (validating the environment) but applies no changes, strictly
 * adhering to the diff status.
 */
public class TaskExecutorRefactoring {

    public static class TaskExecutorProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Defensive Null Check
            if (candidate == null) {
                return false;
            }

            // 2. Type Name Check (Relaxed for NoClasspath)
            // matching "org.springframework.core.task.TaskExecutor"
            String qName = candidate.getQualifiedName();
            return qName != null && qName.contains("org.springframework.core.task.TaskExecutor");
        }

        @Override
        public void process(CtTypeReference<?> element) {
            // Strategy: No-Op
            // The diff indicates the class is UNCHANGED.
            // We simply log the finding to verify the processor is running correctly.
            System.out.println("Verified TaskExecutor usage at " + 
                (element.getPosition().isValidPosition() ? element.getPosition().toString() : "unknown position") + 
                " - No refactoring required.");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve source fidelity
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict source preservation (indentation, whitespace)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TaskExecutorProcessor());

        try {
            launcher.run();
            System.out.println("Processing complete. No changes applied (Source Unchanged).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}