package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * AUTOMATICALLY GENERATED SPOON MIGRATION TOOL
 * 
 * Input Diff Analysis:
 * The provided dependency change diff was empty. 
 * This class serves as a ROBUST TEMPLATE implementing the required constraints 
 * (Sniper Printer, NoClasspath safety) for a hypothetical method rename scenario.
 * 
 * Template Scenario:
 * - Target: com.example.Lib.deprecatedMethod()
 * - Replacement: com.example.Lib.replacementMethod()
 */
public class SpoonMigration {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: Filter by the old method name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"deprecatedMethod".equals(methodName)) {
                return false;
            }

            // 2. Owner Check: Defensive filtering for NoClasspath mode
            // We use loose string matching because type resolution may fail.
            CtExecutableReference<?> execRef = candidate.getExecutable();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();

            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                // If we know the type, ensure it matches our target class (e.g., "Lib")
                // If type is <unknown>, we process it to be safe.
                if (!typeName.contains("Lib") && !typeName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation Logic
            // In this template, we simply rename the method.
            // For complex changes (argument wrapping, type changes), modify the arguments list here.
            
            invocation.getExecutable().setSimpleName("replacementMethod");
            
            System.out.println("Refactored call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java"; // Target source code
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed"; // Output directory

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed");

        // --------------------------------------------------------------------------
        // CRITICAL: PRESERVE FORMATTING & NO-CLASSPATH CONFIGURATION
        // --------------------------------------------------------------------------
        
        // 1. Preserve comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enforce Sniper Printer to preserve indentation and unrelated code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Allow running without full dependencies (Defensive Mode)
        launcher.getEnvironment().setNoClasspath(true);

        // --------------------------------------------------------------------------

        launcher.addProcessor(new MigrationProcessor());
        
        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}