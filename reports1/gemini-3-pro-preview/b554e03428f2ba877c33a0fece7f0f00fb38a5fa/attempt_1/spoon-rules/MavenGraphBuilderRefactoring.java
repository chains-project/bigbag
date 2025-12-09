package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MavenGraphBuilderRefactoring {

    /**
     * Processor to replace usage of the removed internal class Maven31DependencyGraphBuilder
     * with the standard DefaultDependencyGraphBuilder.
     */
    public static class BuilderProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        private static final String REMOVED_CLASS = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
        private static final String REPLACEMENT_SIMPLE_NAME = "DefaultDependencyGraphBuilder";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive check for null
            if (candidate == null) return false;

            // Check using qualified name to avoid resolution issues in NoClasspath mode
            // We use string comparison to identify the specific removed class.
            String qName = candidate.getQualifiedName();
            return qName != null && qName.equals(REMOVED_CLASS);
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Transformation: Rename the type reference.
            // Since the package 'org.apache.maven.shared.dependency.graph.internal' is shared 
            // by both the old and new class, changing the simple name is sufficient.
            // Spoon will handle imports and constructor calls (new Maven31DependencyGraphBuilder()) 
            // automatically by updating the reference.
            
            String oldName = typeRef.getSimpleName();
            typeRef.setSimpleName(REPLACEMENT_SIMPLE_NAME);
            
            System.out.println("Refactored: " + oldName + " -> " + REPLACEMENT_SIMPLE_NAME + 
                               " at " + (typeRef.getPosition().isValidPosition() ? typeRef.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/b554e03428f2ba877c33a0fece7f0f00fb38a5fa/license-maven-plugin/license-maven-plugin/src/main/java/com/mycila/maven/plugin/license/dependencies/MavenProjectLicenses.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b554e03428f2ba877c33a0fece7f0f00fb38a5fa/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/b554e03428f2ba877c33a0fece7f0f00fb38a5fa/license-maven-plugin/license-maven-plugin/src/main/java/com/mycila/maven/plugin/license/dependencies/MavenProjectLicenses.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b554e03428f2ba877c33a0fece7f0f00fb38a5fa/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new BuilderProcessor());
        
        System.out.println("Starting refactoring...");
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}