package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Tool generated for Spoon.
 * 
 * NOTE: No specific dependency diff was provided in the input. 
 * This class serves as a robust template implementing the CRITICAL RULES:
 * 1. SniperJavaPrettyPrinter for high-fidelity source preservation.
 * 2. Defensive NoClasspath handling (checking for null types).
 * 3. Java Generics safety (wildcards).
 * 
 * Placeholder Transformation: Renames 'oldMethod' to 'newMethod' strictly.
 */
public class RefactoringTool {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We target a hypothetical method "oldMethod"
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Defensive Type Check (NoClasspath Compatibility)
            // We cannot rely on resolved types being present.
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> typeRef = target.getType();
                // If type is known (not null) and does NOT match expected owner, skip it.
                // If type is null (unknown), we proceed cautiously (assume it might be the target).
                if (typeRef != null && !typeRef.getQualifiedName().contains("TargetClassName")) {
                    // In a real scenario, "TargetClassName" would be the class owning the method.
                    // If we know for sure it's a different type, we ignore it.
                    // For this template, we comment this out to allow it to run on generic matches.
                    // return false; 
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // LOGIC: Rename the method
            // In NoClasspath, we modify the reference directly or the invocation name.
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored 'oldMethod' to 'newMethod' at line " 
                + (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed");

        // ========================================================================
        // CRITICAL IMPLEMENTATION RULES
        // ========================================================================
        
        // 1. Enable comments to preserve Javadoc and inline comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for high-fidelity preservation
        // (Indentations, whitespace, and unrelated code remain untouched)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // ========================================================================

        launcher.addProcessor(new MethodRenameProcessor());
        
        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}