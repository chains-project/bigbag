package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ServiceRefactoring {

    /**
     * Processor to rename 'performOldAction' to 'performNewAction'
     * on the class 'com.example.LegacyService'.
     */
    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();

            // 1. Name Check (Fast fail)
            if (!"performOldAction".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching because full type resolution might fail without complete JARs.
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            
            // If declaring type is unknown, we might process it safely, or skip based on strictness.
            // Here we check if the type name hints at our target class.
            if (declaringType != null 
                && !declaringType.getQualifiedName().equals("<unknown>") 
                && !declaringType.getQualifiedName().contains("LegacyService")) {
                return false;
            }

            // 3. Argument Count Check (Optional, but good for overloading safety)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring logic: simply rename the executable reference
            invocation.getExecutable().setSimpleName("performNewAction");
            
            System.out.println("Refactored method call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityPostTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityPostTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Sniper & NoClasspath Configuration
        // =========================================================
        
        // 1. Enable comments to ensure they are parsed and attached
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force SniperJavaPrettyPrinter. 
        // This is ESSENTIAL for preserving formatting of untouched code.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath mode allows running without all dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // =========================================================

        launcher.addProcessor(new MethodRenameProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}