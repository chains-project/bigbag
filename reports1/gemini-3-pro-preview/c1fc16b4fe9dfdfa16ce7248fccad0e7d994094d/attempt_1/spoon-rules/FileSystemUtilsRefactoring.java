package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class FileSystemUtilsRefactoring {

    /**
     * Processor for org.springframework.util.FileSystemUtils.
     * <p>
     * ANALYSIS:
     * The input diff indicates: [status=UNCHANGED, binaryCompatible=true].
     * <p>
     * STRATEGY:
     * No AST transformations are applied. This processor identifies and logs usages 
     * of the class to confirm existence, preserving the source code exactly as is
     * via the Sniper printer.
     */
    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Safety check for NoClasspath environment
            if (candidate.getExecutable() == null) return false;

            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) return false;

            // 2. Identify the target class safely
            // Using contains() handles cases where full package might not resolve in NoClasspath
            String qualifiedName = declaringType.getQualifiedName();
            return qualifiedName != null 
                && qualifiedName.contains("org.springframework.util.FileSystemUtils");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // 3. Transformation Logic
            // According to the diff, the class is UNCHANGED. 
            // We perform no modification, simply logging the detection.
            System.out.println("INFO: Found usage of unchanged class 'FileSystemUtils' at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/LPVS/src/main/java/com/lpvs/util/FileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/LPVS/src/main/java/com/lpvs/util/FileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FileSystemUtilsProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Processing complete. No changes applied (Status: UNCHANGED).");
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}