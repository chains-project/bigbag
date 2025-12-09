package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule Generator
 * 
 * Generated based on the provided empty dependency diff.
 * This class serves as a functional TEMPLATE demonstrating the required constraints:
 * 1. Sniper Mode (Preserve formatting/comments)
 * 2. NoClasspath compatibility (Defensive checks)
 * 3. Generic Safety
 * 
 * Hypothetical Scenario Implemented:
 * Renaming `com.example.LegacyClass.oldMethod()` -> `newMethod()`
 */
public class MigrationRefactoring {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Hypothetical target: "oldMethod")
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"oldMethod".equals(methodName)) {
                return false;
            }

            // 2. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String qualifiedName = owner.getQualifiedName();
                // Match specific class or allow unknown in NoClasspath mode
                if (!qualifiedName.contains("LegacyClass") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument Type Check (Defensive coding pattern)
            // Example: If we were checking for a specific argument type "int":
            /*
            if (candidate.getArguments().size() > 0) {
                CtTypeReference<?> argType = candidate.getArguments().get(0).getType();
                // If type is known and NOT the expected type, skip. 
                // We keep null/unknown types to be safe in NoClasspath.
                if (argType != null && !argType.getQualifiedName().equals("int")) {
                     return false; 
                }
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation Logic
            // Example: Rename the method
            invocation.getExecutable().setSimpleName("newMethod");

            System.out.println("Refactored invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bcafa6f39116555a4c53882ed2cf2835594d9114/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bcafa6f39116555a4c53882ed2cf2835594d9114/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        
        // 1. Enable comments to preserve them in the output
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting (indentation, whitespace)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new MigrationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}