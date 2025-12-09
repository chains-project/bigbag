package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RefactoringTemplate {

    /**
     * A template processor for handling breaking changes.
     * Fill in the logic inside isToBeProcessed and process.
     */
    public static class TemplateProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Safety Check: Ensure executable info exists
            if (candidate.getExecutable() == null) return false;

            // TODO: Replace with actual method name from diff
            String targetMethodName = "methodName"; 
            if (!targetMethodName.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Defensive Type Check (NoClasspath Safe)
            // Do not assume .getType() is not null.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // Example: Match class name loosely to avoid resolution errors
            // if (declaringType != null && !declaringType.getQualifiedName().contains("TargetClass")) {
            //     return false; 
            // }

            return false; // Change to true when logic is implemented
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // TODO: Implement transformation logic here
            // Example: Rename method
            // invocation.getExecutable().setSimpleName("newMethodName");
            
            System.out.println("Refactored usage at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProducer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProducer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR CODE PRESERVATION ---
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict exact-match printing
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to handle missing libraries gracefully
        launcher.getEnvironment().setNoClasspath(true);
        // ----------------------------------------------------

        launcher.addProcessor(new TemplateProcessor());

        try {
            launcher.run();
            System.out.println("Spoon processing complete. Output saved to: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}