package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonRefactoring {

    /**
     * Processor to handle breaking changes.
     * Currently configured to handle a Method Renaming scenario:
     * oldMethod() -> newMethod()
     */
    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();
            
            // 1. Name Check (Fastest check first)
            // Replace "oldMethod" with the actual method name from your diff
            if (!"oldMethod".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We check if the declaring type contains the target class name.
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String qualName = declaringType.getQualifiedName();
                // Replace "TargetClassName" with the actual class owning the method
                if (!qualName.contains("TargetClassName") && !qualName.equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument Check (Optional: Filter by argument count/types)
            // Example: if (candidate.getArguments().size() != 2) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Logic
            // Scenario: Renaming the method
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method call at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths - assume running from project root
        String inputPath = "/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: PRESERVE SOURCE CODE FORMATTING (SNIPER MODE)
        // ==========================================================
        
        // 1. Capture comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve whitespace/formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (Defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // ==========================================================

        launcher.addProcessor(new MethodRefactoringProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}