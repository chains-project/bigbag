package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonMigration {

    // TODO: Update these constants based on your actual Diff
    private static final String TARGET_METHOD_NAME = "oldMethodName";
    private static final String NEW_METHOD_NAME = "newMethodName";
    private static final String TARGET_CLASS_NAME = "TargetClassName";

    public static class RefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Fast fail if the method name doesn't match
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Optional - adjust as needed)
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Owner/Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // In NoClasspath, types might be null or <unknown>. 
            // We use string matching to be safe.
            if (declaringType != null 
                && !declaringType.getQualifiedName().equals("<unknown>") 
                && !declaringType.getQualifiedName().contains(TARGET_CLASS_NAME)) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation Logic
            // Example: Renaming the method
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
            
            System.out.println("Refactored " + TARGET_METHOD_NAME + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // ==========================================================
        // CRITICAL IMPLEMENTATION RULES (Sniper & NoClasspath)
        // ==========================================================
        
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Required for Spoon 11+ / Strict Preservation)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);
        // ==========================================================

        launcher.addProcessor(new RefactoringProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}