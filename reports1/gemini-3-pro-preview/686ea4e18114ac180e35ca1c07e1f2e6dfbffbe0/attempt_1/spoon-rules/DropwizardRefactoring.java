package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.HashMap;
import java.util.Map;

public class DropwizardRefactoring {

    /**
     * Processor to handle the relocation of core Dropwizard classes.
     * The diff indicates that io.dropwizard.Application, Bootstrap, and Environment have been removed.
     * In the context of Dropwizard evolution, these classes were moved to the 'io.dropwizard.core' package.
     */
    public static class PackageRelocationProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final Map<String, String> MIGRATION_MAP = new HashMap<>();

        static {
            // Map old FQNs to new FQNs based on Dropwizard Core extraction
            MIGRATION_MAP.put("io.dropwizard.Application", "io.dropwizard.core.Application");
            MIGRATION_MAP.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
            MIGRATION_MAP.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        }

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath mode
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // Check if this type reference matches one of the removed classes.
            // We use strict equality on the Qualified Name to avoid false positives.
            return MIGRATION_MAP.containsKey(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            String oldFqn = candidate.getQualifiedName();
            String newFqn = MIGRATION_MAP.get(oldFqn);

            // Extract the new package structure from the new FQN
            // e.g., "io.dropwizard.core.Application" -> "io.dropwizard.core"
            String newPackageName = newFqn.substring(0, newFqn.lastIndexOf('.'));

            // Mutate the AST: Update the package of the existing reference.
            // By modifying the package, Spoon changes the underlying type definition.
            // The Sniper printer will reflect this change, potentially outputting the FQN
            // if the original import is no longer valid or ambiguous.
            candidate.setPackage(getFactory().Package().getOrCreate(newPackageName));

            System.out.println("Refactored: " + oldFqn + " -> " + newFqn + " at line " + 
                (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/686ea4e18114ac180e35ca1c07e1f2e6dfbffbe0/lithium/src/main/java/com/wire/lithium/Server.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/686ea4e18114ac180e35ca1c07e1f2e6dfbffbe0/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/686ea4e18114ac180e35ca1c07e1f2e6dfbffbe0/lithium/src/main/java/com/wire/lithium/Server.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/686ea4e18114ac180e35ca1c07e1f2e6dfbffbe0/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Sniper Configuration
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting and indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath mode (Defensive Coding)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new PackageRelocationProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}