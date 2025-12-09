package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class FileSystemUtilsRefactoring {

    /**
     * Processor to analyze usages of org.springframework.util.FileSystemUtils.
     * 
     * Analysis based on Input Diff:
     * - CLASS org.springframework.util.FileSystemUtils [status=UNCHANGED]
     * 
     * Refactoring Strategy:
     * - Since the diff reports the class is UNCHANGED, Binary/Source compatible,
     *   no AST transformation is strictly required. 
     * - This processor is configured to identify usages of the class for verification purposes
     *   or as a template for future migrations, but applies no changes to preserve code integrity.
     */
    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtTypeAccess<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeAccess<?> candidate) {
            // 1. Resolve the type reference of the accessed type
            CtTypeReference<?> accessedType = candidate.getAccessedType();

            // 2. Defensive check for nulls (NoClasspath safety)
            if (accessedType == null) {
                return false;
            }

            // 3. Check Qualified Name
            // Using loose matching for robustness in NoClasspath environments
            String qName = accessedType.getQualifiedName();
            if (qName == null || !qName.contains("org.springframework.util.FileSystemUtils")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtTypeAccess<?> typeAccess) {
            // The diff indicates status=UNCHANGED.
            // No transformation logic is applied. 
            // This block is preserved for potential future migrations (e.g., deprecation handling).
            
            System.out.println("Verified usage of FileSystemUtils at line " + 
                (typeAccess.getPosition().isValidPosition() ? typeAccess.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed");

        // CRITICAL SETTINGS: PRESERVE SOURCE CODE STRUCTURE
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing dependencies
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