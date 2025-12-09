package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * 
 * Generated based on the provided dependency diff.
 * Since the input diff was empty, this script implements a **TEMPLATE** strategy
 * for a common refactoring pattern (Method Rename) adhering to strict Sniper/NoClasspath rules.
 * 
 * Update 'TargetClassName' and 'oldMethodName'/'newMethodName' below to use.
 */
public class RefactoringRecipe {

    public static class RefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_CLASS_PARTIAL_NAME = "TargetClassName"; // Replace with actual class name
        private static final String OLD_METHOD_NAME = "oldMethodName";             // Replace with old method name
        private static final String NEW_METHOD_NAME = "newMethodName";             // Replace with new method name

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!OLD_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // In NoClasspath mode, types might be null or <unknown>.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                // Use relaxed matching (contains) rather than strict equality
                boolean isTarget = qName.contains(TARGET_CLASS_PARTIAL_NAME) || qName.equals("<unknown>");
                if (!isTarget) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
            
            System.out.println("Refactored " + OLD_METHOD_NAME + " to " + NEW_METHOD_NAME + 
                               " at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---

        // 1. Preserve Source Code (Sniper Mode)
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // -------------------------------------

        launcher.addProcessor(new RefactoringProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}