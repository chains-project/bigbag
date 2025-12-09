package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.Set;

/**
 * Refactoring Processor for Jasypt Spring Security breaking changes.
 * 
 * ANALYSIS:
 * The diff indicates the removal of PBEPasswordEncoder classes and constructors from:
 * - org.jasypt.spring.security
 * - org.jasypt.spring.security2
 * - org.jasypt.spring.security3
 * 
 * STRATEGY:
 * These removals typically occur when upgrading Jasypt integration for Spring.
 * The standard migration path is to switch to the 'security4' package (org.jasypt.spring.security4).
 * This processor scans for any TypeReference matching the removed packages and redirects
 * them to 'org.jasypt.spring.security4.PBEPasswordEncoder'.
 * 
 * This handles:
 * 1. Import statements.
 * 2. Field declarations.
 * 3. Constructor invocations (new PBEPasswordEncoder()).
 * 4. Method parameters and return types.
 */
public class JasyptRefactoring {

    public static class PBEPasswordEncoderProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final Set<String> REMOVED_CLASSES = Set.of(
            "org.jasypt.spring.security.PBEPasswordEncoder",
            "org.jasypt.spring.security2.PBEPasswordEncoder",
            "org.jasypt.spring.security3.PBEPasswordEncoder"
        );

        private static final String REPLACEMENT_CLASS = "org.jasypt.spring.security4.PBEPasswordEncoder";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath mode
            if (candidate == null) return false;

            // Use getQualifiedName() to match against known removed types
            // This works even if types are not fully resolved in NoClasspath
            String qName = candidate.getQualifiedName();
            return qName != null && REMOVED_CLASSES.contains(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create a new reference to the target class (security4)
            CtTypeReference<?> newRef = getFactory().Type().createReference(REPLACEMENT_CLASS);

            // Replace the old reference in the AST with the new one.
            // This updates imports, variable types, and constructor calls automatically.
            try {
                candidate.replace(newRef);
                System.out.println("Refactored " + candidate.getQualifiedName() + 
                                   " to " + REPLACEMENT_CLASS + 
                                   " at line " + (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown"));
            } catch (Exception e) {
                System.err.println("Failed to refactor usage: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStorePBEPasswordEncoder.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStorePBEPasswordEncoder.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve them in output
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve code structure (indentation, whitespace)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true to process files without full compilation dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new PBEPasswordEncoderProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output saved to " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}