package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule Generator
 * 
 * NOTE: No specific Dependency Diff was provided in the input. 
 * This is a TEMPLATE class implementing the Critical Implementation Rules:
 * 1. Robust Sniper Configuration (Preserves formatting/comments).
 * 2. Defensive Coding (NoClasspath compatibility).
 * 3. Java Generics Safety.
 * 
 * You can adapt the 'TemplateProcessor' below to your specific logic.
 */
public class RefactoringTemplate {

    public static class TemplateProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Example: targeting a method named "oldMethod")
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Example: expects 1 argument)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath - Rule #2)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Logic:
            // - If type is NULL (unknown in NoClasspath), we proceed cautiously (assume it might be the target).
            // - If type is known and matches the NEW type, we skip (already migrated).
            // - If type is known and matches the OLD type, we process.
            
            // Example: Skipping if already migrated to "NewType"
            if (type != null && type.getQualifiedName().contains("NewType")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // Check if owner is known and is NOT the target class (Example: "TargetClass")
            if (owner != null && !owner.getQualifiedName().contains("TargetClass") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // LOGIC PLACEHOLDER: Perform AST transformation here.
            // Example: Rename method to "newMethod"
            invocation.getExecutable().setSimpleName("newMethod");

            System.out.println("Refactored code at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULE #1: Preserve Source Code
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for high-fidelity preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // CRITICAL IMPLEMENTATION RULE #2: NoClasspath Compatibility
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TemplateProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}