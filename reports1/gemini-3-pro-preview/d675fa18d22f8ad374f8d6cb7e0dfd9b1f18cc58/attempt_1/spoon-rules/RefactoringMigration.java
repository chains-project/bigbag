package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule Generator
 * 
 * NOTE: The input <dependency_change_diff> was empty. 
 * This class provides a robust template configured with Sniper Mode (Source Preservation)
 * and Defensive Coding (NoClasspath) patterns.
 * 
 * Template Logic: Renames 'oldMethod' to 'newMethod'.
 */
public class RefactoringMigration {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();
            
            // 1. Safety Check: Ensure executable reference exists
            if (executable == null) return false;

            // 2. Name Check: Target the specific method name (PLACEHOLDER: 'oldMethod')
            if (!"oldMethod".equals(executable.getSimpleName())) {
                return false;
            }

            // 3. Owner/Class Check (Defensive for NoClasspath)
            // We use string matching because types might not resolve without full classpath
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // PLACEHOLDER: Replace 'TargetClassName' with the actual class changing
                if (!qualifiedName.contains("TargetClassName") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Argument Check (Optional: verify signature matches old version)
            // Example: if (candidate.getArguments().size() != 1) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Logic
            // PLACEHOLDER: Renaming the method to 'newMethod'
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/NotificationTemplateProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/NotificationTemplateProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Configuration (NoClasspath mode)
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // Add the processor
        launcher.addProcessor(new MigrationProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}