package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * Generated based on the provided dependency diff.
 * 
 * NOTE: The input diff was empty. This is a TEMPLATE showing the required
 * configuration for Source Preservation (Sniper) and NoClasspath safety.
 *
 * Customize the 'isToBeProcessed' and 'process' methods for your specific migration.
 */
public class RefactoringTemplate {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // TODO: Replace "targetMethodName" with the method name from your diff
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"targetMethodName".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            // TODO: Adjust expected argument count
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // Example: If the first argument is already the new type, skip processing.
            // checking for null is CRITICAL in NoClasspath mode.
            if (argType != null && argType.getQualifiedName().contains("NewType")) {
                return false;
            }

            // 4. Owner/Scope Check
            // We check the declaring type of the method being called.
            CtExecutableReference<?> execRef = candidate.getExecutable();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            // Defensive check: declaringType might be null or unknown in NoClasspath
            if (declaringType != null) {
                String qualName = declaringType.getQualifiedName();
                // TODO: Replace "TargetClassName" with the class owning the method
                if (!qualName.contains("TargetClassName") && !qualName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // TODO: Implement your transformation logic here.
            // Example: Renaming the method
            invocation.getExecutable().setSimpleName("newMethodName");
            
            System.out.println("Refactored usage at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/f5a34301592bb62474489de79069d7873ffe070e/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5a34301592bb62474489de79069d7873ffe070e/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f5a34301592bb62474489de79069d7873ffe070e/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5a34301592bb62474489de79069d7873ffe070e/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULE: Preserve Source Code (Sniper)
        // 1. Enable comments to prevent them from being stripped
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding: Enable NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types/libs
        launcher.getEnvironment().setIgnoreSyntaxErrors(true); 

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