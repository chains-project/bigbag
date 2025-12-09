package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * AUTO-GENERATED REFACORING SCRIPT
 * 
 * NOTE: The input <dependency_change_diff> was empty. 
 * This script demonstrates the required structure and constraints (Sniper, NoClasspath) 
 * for a hypothetical breaking change:
 * 
 * - METHOD com.example.Legacy.deprecatedMethod() [REMOVED]
 * + METHOD com.example.Legacy.modernMethod() [ADDED]
 */
public class LegacyMigration {

    public static class LegacyMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: Target the old method name
            if (!"deprecatedMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            // In NoClasspath, declaring types might be null or <unknown>.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            
            // If the owner is known and does NOT match our target class, skip it.
            if (owner != null 
                && !owner.getQualifiedName().contains("Legacy") 
                && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Argument Check (Optional based on signature)
            // e.g., if (candidate.getArguments().size() != 0) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method invocation
            // Since we are in NoClasspath, we modify the reference directly.
            invocation.getExecutable().setSimpleName("modernMethod");

            System.out.println("Refactored 'deprecatedMethod' to 'modernMethod' at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or passed as args)
        String inputPath = "/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed");

        // =========================================================
        // CRITICAL CONFIGURATION: PRESERVE FORMATTING & ROBUSTNESS
        // =========================================================

        // 1. Enable comments (essential for Sniper)
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive NoClasspath mode (Assume libraries are missing)
        launcher.getEnvironment().setNoClasspath(true);

        // =========================================================

        launcher.addProcessor(new LegacyMethodProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring execution finished.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}