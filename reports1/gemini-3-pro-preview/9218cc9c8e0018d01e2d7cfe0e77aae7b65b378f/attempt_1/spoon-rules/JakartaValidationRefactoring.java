package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class JakartaValidationRefactoring {

    /**
     * Processor to migrate javax.validation types to jakarta.validation.
     * This handles variable declarations, return types, parameters, and annotations.
     */
    public static class JavaxToJakartaProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding: Ensure package exists (NoClasspath safety)
            if (candidate.getPackage() == null) {
                return false;
            }

            String packName = candidate.getPackage().getQualifiedName();
            // Check if package name is javax.validation or a subpackage
            return packName != null && (packName.equals("javax.validation") || packName.startsWith("javax.validation."));
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            CtPackageReference oldPackage = ref.getPackage();
            String oldName = oldPackage.getQualifiedName();

            // Calculate new package name (javax -> jakarta)
            String newName = oldName.replace("javax.validation", "jakarta.validation");

            // Create new package reference
            CtPackageReference newPackage = getFactory().Core().createPackageReference();
            newPackage.setSimpleName(newName);

            // Update the reference to point to the new package
            ref.setPackage(newPackage);
        }
    }

    /**
     * Processor specifically for Imports.
     * Ensures that import statements (e.g., import javax.validation.NotNull;) are updated.
     */
    public static class ImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport ctImport) {
            // Handle imports that reference types
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) ctImport.getReference();
                if (ref.getPackage() != null) {
                    String packName = ref.getPackage().getQualifiedName();
                    if (packName != null && packName.startsWith("javax.validation")) {
                        // Calculate new package name
                        String newName = packName.replace("javax.validation", "jakarta.validation");
                        
                        // Create and set new package
                        CtPackageReference newPackage = getFactory().Core().createPackageReference();
                        newPackage.setSimpleName(newName);
                        ref.setPackage(newPackage);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9218cc9c8e0018d01e2d7cfe0e77aae7b65b378f/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9218cc9c8e0018d01e2d7cfe0e77aae7b65b378f/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9218cc9c8e0018d01e2d7cfe0e77aae7b65b378f/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9218cc9c8e0018d01e2d7cfe0e77aae7b65b378f/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Sniper
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath Compatibility (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processors
        launcher.addProcessor(new JavaxToJakartaProcessor());
        // Add ImportProcessor to handle import statements explicitly
        launcher.addProcessor(new ImportProcessor());

        try {
            // Run the transformation
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}