package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring script to handle the removal of org.codehaus.plexus.util.xml.Xpp3Dom.
 * 
 * Migration Strategy:
 * The class Xpp3Dom was removed from plexus-utils. The standard migration path in the 
 * Maven ecosystem is to use the equivalent class from maven-shared-utils:
 * 'org.apache.maven.shared.utils.xml.Xpp3Dom'.
 * 
 * This processor scans for all references to the old class and replaces them with 
 * the new class.
 */
public class Xpp3DomRefactoring {

    public static class Xpp3DomProcessor extends AbstractProcessor<CtTypeReference<?>> {
        // The class identified as REMOVED in the diff
        private static final String OLD_CLASS_NAME = "org.codehaus.plexus.util.xml.Xpp3Dom";
        // The inferred replacement class (maven-shared-utils)
        private static final String NEW_CLASS_NAME = "org.apache.maven.shared.utils.xml.Xpp3Dom";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive Checks for NoClasspath environment
            if (candidate == null) return false;
            
            // Ignore implicit references (e.g., inferred types, diamond operators)
            // to ensure we only modify code actually written in the source file.
            if (candidate.isImplicit()) return false;

            // Robust check using Qualified Name
            // In NoClasspath, getQualifiedName() attempts to resolve via imports.
            String qName = candidate.getQualifiedName();
            return OLD_CLASS_NAME.equals(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create a reference to the new type
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_CLASS_NAME);

            // Replace the old reference with the new one.
            // This handles imports, variable declarations, parameters, return types, 
            // and constructor calls (new Xpp3Dom()).
            candidate.replace(newTypeRef);

            // Log the refactoring for the user
            CtElement parent = candidate.getParent();
            String location = "unknown location";
            if (parent != null && parent.getPosition() != null && parent.getPosition().isValidPosition()) {
                location = "line " + parent.getPosition().getLine();
            }
            System.out.println("Refactored Xpp3Dom usage at " + location);
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified or passed as args)
        String inputPath = "/home/kth/Documents/last_transformer/output/cd5bb39f43e4570b875027073da3d4e43349ead1/pgpverify-maven-plugin/src/main/java/org/simplify4u/plugins/utils/MavenCompilerUtils.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cd5bb39f43e4570b875027073da3d4e43349ead1/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/cd5bb39f43e4570b875027073da3d4e43349ead1/pgpverify-maven-plugin/src/main/java/org/simplify4u/plugins/utils/MavenCompilerUtils.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cd5bb39f43e4570b875027073da3d4e43349ead1/attempt_1/transformed");

        // CRITICAL CONFIGURATION for Source Preservation
        // 1. Enable comments to keep license headers and javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Use SniperJavaPrettyPrinter to print only modified parts and preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new Xpp3DomProcessor());

        try {
            System.out.println("Starting Xpp3Dom refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Modified code saved to: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}