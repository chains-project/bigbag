package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script generated for Dependency Migration.
 * 
 * INPUT DIFF ANALYSIS:
 * Since the input diff was empty, this script implements a robust TEMPLATE
 * for a common refactoring scenario: Method Renaming.
 * 
 * HYPOTHETICAL CHANGE:
 * - OLD: com.api.Utils.doWork()
 * + NEW: com.api.Utils.performTask()
 */
public class UtilsRefactoring {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();

            // 1. Name Check
            // We look for the old method name "doWork"
            if (!"doWork".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            // in NoClasspath mode, declaring type might be null or partial.
            // We use loose matching on the class name.
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // If the type is known and strictly NOT our target class, skip.
                // We assume the class is "Utils".
                if (!qualifiedName.contains("Utils") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }
            
            // 3. Argument Count Check (Optional but recommended)
            if (candidate.getArguments().size() != 0) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename "doWork" to "performTask"
            invocation.getExecutable().setSimpleName("performTask");
            
            System.out.println("Refactored 'doWork' to 'performTask' at " + 
                invocation.getPosition().getFile().getName() + ":" + 
                invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/attempt_1/transformed");

        // ==========================================================
        // CRITICAL IMPLEMENTATION RULES
        // ==========================================================
        
        // 1. Preserve Source Code (Sniper Mode)
        // Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        // Force SniperJavaPrettyPrinter for high-fidelity transformations
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        // Ensure the processor runs even if dependencies are missing
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new MethodRenameProcessor());

        // Run the transformation
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}