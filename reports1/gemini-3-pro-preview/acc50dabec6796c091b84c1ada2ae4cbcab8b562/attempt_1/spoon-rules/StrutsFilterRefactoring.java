package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class StrutsFilterRefactoring {

    /**
     * Processor to handle the relocation of StrutsPrepareAndExecuteFilter.
     * Old: org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
     * New: org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter
     */
    public static class StrutsFilterProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS_NAME = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
        private static final String NEW_CLASS_NAME = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive checks for NoClasspath scenarios
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // Check if this reference points to the removed class
            // We use string comparison to avoid ClassNotFoundException in NoClasspath mode
            return OLD_CLASS_NAME.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create a reference to the new class location
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_CLASS_NAME);

            // Replace the old reference with the new one. 
            // Spoon/Sniper will handle the rewriting of the source code.
            candidate.replace(newTypeRef);

            System.out.println("Refactored StrutsPrepareAndExecuteFilter at " 
                + (candidate.getPosition().isValidPosition() ? "line " + candidate.getPosition().getLine() : "unknown position"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/acc50dabec6796c091b84c1ada2ae4cbcab8b562/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/acc50dabec6796c091b84c1ada2ae4cbcab8b562/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/acc50dabec6796c091b84c1ada2ae4cbcab8b562/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/acc50dabec6796c091b84c1ada2ae4cbcab8b562/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Code Preservation
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for precise edits
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StrutsFilterProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}