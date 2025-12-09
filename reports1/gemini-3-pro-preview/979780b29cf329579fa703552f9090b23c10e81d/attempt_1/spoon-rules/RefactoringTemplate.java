package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * 
 * NOTE: The input diff was empty. This is a template implementation demonstrating
 * the required Sniper/NoClasspath configuration and a generic method rename pattern.
 * 
 * Scenario: Rename 'oldMethod' to 'newMethod' in class 'TargetClass'.
 */
public class RefactoringTemplate {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Verify the method name matches the one to be removed/renamed
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching on QualifiedName to avoid resolution errors
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            
            // If the type is unknown, we might process it aggressively, or skip.
            // Here we check if it matches the target class if the type is known.
            if (owner != null && !owner.getQualifiedName().equals("<unknown>")) {
                if (!owner.getQualifiedName().contains("TargetClass")) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method invocation
            invocation.getExecutable().setSimpleName("newMethod");
            
            // Print location for debugging
            if (invocation.getPosition().isValidPosition()) {
                System.out.println("Refactored 'oldMethod' at " + invocation.getPosition());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/RequestTemplateProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/RequestTemplateProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed");

        // -------------------------------------------------------------
        // CRITICAL IMPLEMENTATION RULES
        // -------------------------------------------------------------
        
        // 1. Preserve Source Code (Robust Sniper Configuration)
        // Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        // Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        // Assume full classpath is not available
        launcher.getEnvironment().setNoClasspath(true);

        // -------------------------------------------------------------

        launcher.addProcessor(new MethodRenameProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}