package org.tinspin.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class TinSpinRefactoring {

    /**
     * Processor to handle the renaming of PointDistanceFunction to PointDistance.
     * This addresses the removal of PointDistanceFunction and the update of method signatures
     * in classes like CoverTree.
     */
    public static class PointDistanceRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive check for null
            if (candidate == null) {
                return false;
            }

            // 1. Check Simple Name (Fast check)
            if (!"PointDistanceFunction".equals(candidate.getSimpleName())) {
                return false;
            }

            // 2. Check Qualified Name (Safety for NoClasspath)
            // We want to target org.tinspin.index.PointDistanceFunction
            // In NoClasspath, getQualifiedName() typically returns what is available from source/imports.
            String qName = candidate.getQualifiedName();
            return qName != null && qName.contains("org.tinspin.index.PointDistanceFunction");
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Refactoring Strategy: Rename the type.
            // Existing: org.tinspin.index.PointDistanceFunction
            // New:      org.tinspin.index.PointDistance
            
            // This handles variable declarations, method parameters, return types, 
            // and class implements/extends clauses.
            candidate.setSimpleName("PointDistance");
            
            // Note: We do not change the package, as both exist in 'org.tinspin.index'.
            // Spoon's Sniper printer will preserve existing formatting around this change.
            System.out.println("Refactored PointDistanceFunction to PointDistance at " + 
                (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths - meant to be adjusted by the user or passed as args
        String inputPath = "/home/kth/Documents/last_transformer/output/e70bd198fa1e1d65f86e071b8ebdd021141cfa95/PGS/src/main/java/micycle/pgs/PGS_Meshing.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e70bd198fa1e1d65f86e071b8ebdd021141cfa95/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e70bd198fa1e1d65f86e071b8ebdd021141cfa95/PGS/src/main/java/micycle/pgs/PGS_Meshing.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e70bd198fa1e1d65f86e071b8ebdd021141cfa95/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments to preserve license headers and Javadoc
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting (whitespace, indentation)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PointDistanceRenameProcessor());

        try {
            System.out.println("Starting TinSpin Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}