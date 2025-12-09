package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class GeneratedRefactoring {

    /**
     * Processor to handle the refactoring.
     * Note: As the input diff was empty, this processor implements a generic
     * template for a method rename: 'oldMethod' -> 'newMethod'.
     * You can adapt the string literals in 'isToBeProcessed' to your specific needs.
     */
    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Replace "oldMethod" with the actual method name from your diff
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"oldMethod".equals(methodName)) {
                return false;
            }

            // 2. Owner Check (Defensive / NoClasspath)
            // We check if the method belongs to the target class (fuzzy match).
            // Replace "TargetClass" with the class name from your diff.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Using contains to handle cases where package resolution might fail in NoClasspath
                if (!qualifiedName.contains("TargetClass") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method
            // Replace "newMethod" with the new method name from your diff
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/http/CachingProxyITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/http/CachingProxyITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        
        // 1. Enable comments (Crucial for source preservation)
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Preserves indentation and formatting)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Defensive coding assumption)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MethodRenameProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}