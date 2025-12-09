package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MockitoRunnerRefactoring {

    /**
     * Processor to handle the removal of org.mockito.runners.MockitoJUnitRunner.
     * It migrates references to the new package: org.mockito.junit.MockitoJUnitRunner.
     */
    public static class MockitoRunnerProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Defensive Checks for NoClasspath
            if (candidate == null || candidate.getPackage() == null) {
                return false;
            }

            // 2. Match the specific removed Class
            // We use getQualifiedName() to handle both FQCN usage and Imports.
            return "org.mockito.runners.MockitoJUnitRunner".equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // 3. Transformation: Move to 'org.mockito.junit' package
            // This is the standard replacement for Mockito 2.x+
            candidate.setPackage(getFactory().Package().getOrCreate("org.mockito.junit"));

            // Log the change
            String position = candidate.getPosition().isValidPosition() 
                ? "line " + candidate.getPosition().getLine() 
                : "unknown position";
            System.out.println("Refactored MockitoJUnitRunner at " + position);
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ff8b5b61548d50cf60b77784a181e917cb35033b/junit-quickcheck/core/src/test/java/com/pholser/junit/quickcheck/internal/generator/RegisterGeneratorsByConventionTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ff8b5b61548d50cf60b77784a181e917cb35033b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ff8b5b61548d50cf60b77784a181e917cb35033b/junit-quickcheck/core/src/test/java/com/pholser/junit/quickcheck/internal/generator/RegisterGeneratorsByConventionTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ff8b5b61548d50cf60b77784a181e917cb35033b/attempt_1/transformed");

        // CRITICAL: Configure Sniper Printer for source code preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Set NoClasspath to handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MockitoRunnerProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}