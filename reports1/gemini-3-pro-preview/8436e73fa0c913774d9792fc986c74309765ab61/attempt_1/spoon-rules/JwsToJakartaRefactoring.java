package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring script to migrate from javax.jws.* to jakarta.jws.*
 * based on the removal of javax.jws classes in the dependency update.
 */
public class JwsToJakartaRefactoring {

    /**
     * Processor to rename usage of javax.jws types to jakarta.jws.
     * Handles:
     * - javax.jws.WebMethod
     * - javax.jws.WebParam
     * - javax.jws.WebResult
     * - javax.jws.WebService
     * - javax.jws.soap.SOAPBinding
     * - And their inner types/fields.
     */
    public static class JwsRenamingProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive programming: check for null
            if (candidate == null) {
                return false;
            }

            // In NoClasspath mode, we rely on the qualified name found in source.
            // If resolution fails, this might return the simple name, so this check is safe.
            String qn = candidate.getQualifiedName();

            // We only care about types starting with the removed package javax.jws.
            // This covers javax.jws.soap.SOAPBinding as well.
            return qn != null && qn.startsWith("javax.jws.");
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            String currentQn = candidate.getQualifiedName();
            
            // Logic: Perform the package rename (Java EE -> Jakarta EE migration pattern)
            String newQn = currentQn.replace("javax.jws.", "jakarta.jws.");

            // Create a new reference with the updated package
            CtTypeReference<?> newRef = getFactory().Type().createReference(newQn);

            // Replace the old reference in the AST
            try {
                candidate.replace(newRef);
                System.out.println("Refactored: " + currentQn + " -> " + newQn + " at line " + candidate.getPosition().getLine());
            } catch (Exception e) {
                System.err.println("Failed to refactor " + currentQn + ": " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified or passed as args in a real CLI)
        String inputPath = "/home/kth/Documents/last_transformer/output/8436e73fa0c913774d9792fc986c74309765ab61/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/webservices/documents/FatcorewsPort.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8436e73fa0c913774d9792fc986c74309765ab61/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/8436e73fa0c913774d9792fc986c74309765ab61/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/webservices/documents/FatcorewsPort.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8436e73fa0c913774d9792fc986c74309765ab61/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting and indentation strictly
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true to allow running without full dependency resolution
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new JwsRenamingProcessor());

        // Run the transformation
        try {
            launcher.run();
            System.out.println("Migration complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}