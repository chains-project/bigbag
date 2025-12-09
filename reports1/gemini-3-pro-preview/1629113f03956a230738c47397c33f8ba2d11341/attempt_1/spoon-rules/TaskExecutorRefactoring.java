package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class TaskExecutorRefactoring {

    /**
     * Processor for org.springframework.core.task.TaskExecutor.
     * <p>
     * ANALYSIS:
     * The provided dependency diff indicates that 'org.springframework.core.task.TaskExecutor'
     * has [status=UNCHANGED]. Therefore, no breaking changes (methods removed, types changed)
     * exist to be refactored.
     * <p>
     * STRATEGY:
     * This processor is configured to identify references to the class for verification purposes,
     * but intentionally performs NO transformations to ensure source code preservation.
     */
    public static class TaskExecutorProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // Target TypeReferences to identify usage of the class
            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
                // Defensive check: verify name is not null and matches target
                // Using exact match as package is known
                return ref.getQualifiedName() != null 
                    && ref.getQualifiedName().equals("org.springframework.core.task.TaskExecutor");
            }
            return false;
        }

        @Override
        public void process(CtElement element) {
            // No refactoring required as status is UNCHANGED.
            // Logic explicitly omitted to preserve original source code.
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/LicensePreValidationSystem.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed");

        // CRITICAL SETTINGS: PRESERVE SOURCE CODE STRUCTURE
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for NoClasspath execution
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TaskExecutorProcessor());

        try {
            launcher.run();
            System.out.println("Processor executed. No changes applied (Source Class UNCHANGED).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}