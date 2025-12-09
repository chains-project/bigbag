package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Processor for the removal of 'de.gwdg.metadataqa.api.json.JsonBranch'.
 * 
 * ANALYSIS:
 * - The class 'de.gwdg.metadataqa.api.json.JsonBranch' was removed.
 * - The class 'de.gwdg.metadataqa.api.model.pathcache.JsonPathCache' is unchanged.
 * - Strategy: Since the class is removed with no specified replacement, this processor 
 *   identifies all references to 'JsonBranch' and renames them to 'JsonBranch_REMOVED'.
 *   This explicitly breaks compilation at the exact usage sites, alerting developers 
 *   to the necessary manual intervention (migration or deletion).
 */
public class JsonBranchRefactoring {

    public static class JsonBranchRemovalProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String REMOVED_CLASS_QNAME = "de.gwdg.metadataqa.api.json.JsonBranch";
        private static final String REPLACEMENT_SUFFIX = "_REMOVED";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath
            if (candidate == null) return false;

            // Check qualified name robustly
            String qName = candidate.getQualifiedName();
            if (qName == null) return false;

            // Use string matching as per instructions (robust against resolution issues)
            // We match the specific removed class.
            return qName.equals(REMOVED_CLASS_QNAME);
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            // Avoid renaming the package part if it's fully qualified in code,
            // just rename the simple name to break it visibly.
            
            // Current simple name (e.g., "JsonBranch")
            String oldName = ref.getSimpleName();
            
            // Check if it's already refactored (idempotency)
            if (oldName.endsWith(REPLACEMENT_SUFFIX)) {
                return;
            }

            // Transformation: Append suffix to indicate removal
            String newName = oldName + REPLACEMENT_SUFFIX;
            ref.setSimpleName(newName);

            // Optional: Add a comment if possible (Context dependent, handled by renaming for now)
            System.out.println("Refactored usage of removed class '" + oldName + "' to '" + newName + "' at " + 
                (ref.getPosition().isValidPosition() ? ref.getPosition().toString() : "unknown position"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/43c824a24b09efd8b4b00449ce31cd121a6b23eb/qa-catalogue/src/main/java/de/gwdg/metadataqa/marc/MarcFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/43c824a24b09efd8b4b00449ce31cd121a6b23eb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/43c824a24b09efd8b4b00449ce31cd121a6b23eb/qa-catalogue/src/main/java/de/gwdg/metadataqa/marc/MarcFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/43c824a24b09efd8b4b00449ce31cd121a6b23eb/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath Compatibility
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new JsonBranchRemovalProcessor());

        try {
            System.out.println("Starting refactoring: 'JsonBranch' Removal...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}