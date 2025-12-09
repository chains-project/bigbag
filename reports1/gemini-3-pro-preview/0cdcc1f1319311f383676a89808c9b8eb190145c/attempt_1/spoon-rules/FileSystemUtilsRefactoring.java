package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class FileSystemUtilsRefactoring {

    /**
     * Processor designed to handle 'org.springframework.util.FileSystemUtils'.
     * 
     * Analysis of Diff:
     * - Status: UNCHANGED
     * - Binary Compatible: True
     * - Source Compatible: True
     * 
     * Strategy:
     * This processor identifies usages of the class but performs NO transformation (No-Op),
     * ensuring source code is preserved exactly as is via the Sniper printer.
     */
    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtTypeAccess<?>> {
        
        @Override
        public boolean isToBeProcessed(CtTypeAccess<?> candidate) {
            CtTypeReference<?> accessedType = candidate.getAccessedType();

            // Defensive Coding: Handle NoClasspath scenario where types might be null
            if (accessedType == null) {
                return false;
            }

            // Loose matching to catch qualified names without requiring full classpath resolution
            String qName = accessedType.getQualifiedName();
            return qName != null && qName.contains("org.springframework.util.FileSystemUtils");
        }

        @Override
        public void process(CtTypeAccess<?> element) {
            // Per the diff, the class is Unchanged.
            // We log the discovery to prove the processor ran, but apply no changes.
            System.out.println("Verified valid usage of FileSystemUtils at " 
                + element.getPosition().getFile().getName() 
                + ":" + element.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/util/FileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/util/FileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of unchanged code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Set NoClasspath to true for robust execution without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FileSystemUtilsProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring scan complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}