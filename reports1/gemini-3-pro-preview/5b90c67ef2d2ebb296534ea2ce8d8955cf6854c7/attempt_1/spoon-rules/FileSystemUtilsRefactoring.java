package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Processor for org.springframework.util.FileSystemUtils.
 * 
 * ANALYSIS OF DIFF:
 * The provided dependency diff indicates:
 * "- CLASS org.springframework.util.FileSystemUtils [status=UNCHANGED, binaryCompatible=true, sourceCompatible=true]"
 * 
 * Since the status is UNCHANGED, there are no breaking changes (methods removed, types changed) that require 
 * automated AST transformation. This processor is generated as an auditing tool to identify usages of the class,
 * but performs no mutations to preserve the existing valid code.
 */
public class FileSystemUtilsRefactoring {

    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Defensive check for executable declaration
            if (candidate.getExecutable() == null) return false;

            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();

            // Check 1: Null safety (NoClasspath mode)
            if (declaringType == null) return false;

            // Check 2: Match the class name from the diff
            // Using loose matching for robustness in NoClasspath scenarios
            if (declaringType.getQualifiedName().contains("org.springframework.util.FileSystemUtils")) {
                return true;
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // No transformation is applied because the diff reports the class is UNCHANGED.
            // This log serves as confirmation that the processor successfully identified the usage.
            System.out.println("Audit: Found usage of FileSystemUtils at line " 
                + invocation.getPosition().getLine() 
                + ". Status is UNCHANGED, so no refactoring logic is executed.");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FileSystemUtilsProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}