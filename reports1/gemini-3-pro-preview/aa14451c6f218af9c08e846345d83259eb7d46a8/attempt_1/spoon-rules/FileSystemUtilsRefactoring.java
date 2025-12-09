package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class FileSystemUtilsRefactoring {

    /**
     * Processor to identify usages of org.springframework.util.FileSystemUtils.
     * 
     * NOTE: The provided diff indicated the class status as [UNCHANGED].
     * Therefore, this processor identifies usages but performs no transformations.
     * It serves as a defensive boilerplate should future changes (e.g., deprecations) occur.
     */
    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtTypeAccess<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeAccess<?> candidate) {
            // 1. Resolve the type being accessed
            CtTypeReference<?> typeRef = candidate.getAccessedType();
            
            // 2. Defensive Check: Ensure type is not null (NoClasspath safety)
            if (typeRef == null) {
                return false;
            }

            // 3. Name Check: Match fully qualified name loosely to handle NoClasspath
            // We check for "FileSystemUtils" specifically in the Spring package structure
            String qName = typeRef.getQualifiedName();
            return qName != null && qName.equals("org.springframework.util.FileSystemUtils");
        }

        @Override
        public void process(CtTypeAccess<?> typeAccess) {
            // According to the input diff, the status is UNCHANGED.
            // Logic is intentionally left blank aside from logging.
            // If specific methods were removed/modified, transformation logic would go here.
            System.out.println("Detected usage of unchanged class FileSystemUtils at line " 
                + typeAccess.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/util/FileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/util/FileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve code formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new FileSystemUtilsProcessor());

        try {
            System.out.println("Starting refactoring analysis...");
            launcher.run();
            System.out.println("Analysis complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}