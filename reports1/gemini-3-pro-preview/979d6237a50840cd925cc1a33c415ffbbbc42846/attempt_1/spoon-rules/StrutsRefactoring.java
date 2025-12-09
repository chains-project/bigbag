package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class StrutsRefactoring {

    /**
     * Processor to handle the relocation of StrutsPrepareAndExecuteFilter.
     * Old: org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
     * New: org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter
     */
    public static class StrutsFilterProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
        private static final String NEW_PACKAGE = "org.apache.struts2.dispatcher.filter";
        private static final String CLASS_NAME = "StrutsPrepareAndExecuteFilter";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding: handle nulls in NoClasspath mode
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // Check if this reference points to the removed class
            return OLD_CLASS.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            Factory factory = getFactory();

            // Create reference to the new location
            CtTypeReference<?> newTypeRef = factory.Type().createReference(NEW_PACKAGE + "." + CLASS_NAME);

            // Replace the old reference with the new one
            // This ensures that imports and usages are updated according to Spoon's model
            candidate.replace(newTypeRef);

            System.out.println("Refactored StrutsPrepareAndExecuteFilter at line " 
                + (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/979d6237a50840cd925cc1a33c415ffbbbc42846/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979d6237a50840cd925cc1a33c415ffbbbc42846/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/979d6237a50840cd925cc1a33c415ffbbbc42846/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979d6237a50840cd925cc1a33c415ffbbbc42846/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Refactoring
        
        // 1. Enable comments to ensure they aren't stripped
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new StrutsFilterProcessor());

        try {
            System.out.println("Starting Struts refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}