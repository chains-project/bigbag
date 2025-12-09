package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Rule.
 * 
 * Generated for: [Missing Input Diff]
 * Placeholder Strategy: Renames 'deprecatedMethod' to 'newMethod'.
 */
public class ApiRefactoring {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fast fail)
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"deprecatedMethod".equals(methodName)) {
                return false;
            }

            // 2. Owner/Scope Check (Defensive for NoClasspath)
            CtExecutableReference<?> execRef = candidate.getExecutable();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();

            // We check if the class name contains the target class (loose matching)
            // or if it's unknown/null (NoClasspath assumption)
            if (declaringType != null 
                && !declaringType.getQualifiedName().equals("<unknown>") 
                && !declaringType.getQualifiedName().contains("TargetClassName")) {
                return false;
            }

            // 3. Argument Check (Optional: Adjust based on method signature)
            // Example: Accepting any number of arguments for this rename
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Logic: Rename the method invocation
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths - can be overridden by args
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---
        
        // 1. Preserve Source Code (Sniper Configuration)
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);
        
        // -------------------------------------

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