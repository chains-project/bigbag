package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool
 * Generated Template for NoClasspath Refactoring with Sniper Printer.
 */
public class SpoonMigration {

    public static class RefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // TODO: Update these constants based on your actual Diff
        private static final String TARGET_METHOD_NAME = "oldMethod";
        private static final String NEW_METHOD_NAME = "newMethod";
        private static final String TARGET_CLASS_NAME = "TargetClassName";
        private static final int TARGET_ARG_COUNT = 1;

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != TARGET_ARG_COUNT) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // We check the first argument to ensure it matches expectations
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Rule: NEVER assume type is not null.
            // Rule: If type is null (unknown), we process it conservatively.
            // Rule: If we can resolve it and it doesn't match our target, we skip.
            if (type != null && !type.getQualifiedName().equals("java.lang.String") && !type.getQualifiedName().equals("<unknown>")) {
                // Example: If we expect a String, but find an Integer, skip.
                // Note: In NoClasspath, simple names might be returned, so be careful with strict checks.
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains(TARGET_CLASS_NAME) && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Logic: Rename the method
            // For complex transformations (like argument wrapping), use getFactory() here.
            
            String oldName = invocation.getExecutable().getSimpleName();
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
            
            System.out.println("Refactored: " + oldName + " -> " + NEW_METHOD_NAME + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/a0ec50cb297c4202e138627859a89ef032fa78ab/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a0ec50cb297c4202e138627859a89ef032fa78ab/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/a0ec50cb297c4202e138627859a89ef032fa78ab/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a0ec50cb297c4202e138627859a89ef032fa78ab/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION START ---
        
        // 1. Enable Comments (Essential for preserving headers/javadocs)
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer (Essential for preserving formatting/indentation)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath (Essential for running without full dependency JARs)
        launcher.getEnvironment().setNoClasspath(true);
        
        // --- CRITICAL CONFIGURATION END ---

        launcher.addProcessor(new RefactoringProcessor());

        try {
            System.out.println("Starting refactoring on: " + inputPath);
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}