package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class DssRefactoring {

    /**
     * Processor to handle the relocation of CertificationPermission.
     * Change: eu.europa.esig.dss.pades.CertificationPermission -> eu.europa.esig.dss.enumerations.CertificationPermission
     */
    public static class CertificationPermissionProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        private static final String OLD_CLASS_QNAME = "eu.europa.esig.dss.pades.CertificationPermission";
        private static final String NEW_PACKAGE_NAME = "eu.europa.esig.dss.enumerations";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive Check: Ensure candidate is valid
            if (candidate == null) return false;

            // In NoClasspath, getQualifiedName() relies on imports/syntax.
            // We check if the reference matches the removed class.
            String qName = candidate.getQualifiedName();
            return OLD_CLASS_QNAME.equals(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            Factory factory = getFactory();
            
            // Strategy: Update the package of the existing reference.
            // This preserves the simple name usage in the code while updating the underlying type,
            // allowing Spoon to update imports or fully qualified names as necessary.
            
            CtPackage newPackage = factory.Package().getOrCreate(NEW_PACKAGE_NAME);
            candidate.setPackage(newPackage);

            System.out.println("Refactored CertificationPermission in: " + 
                (candidate.getPosition().isValidPosition() ? candidate.getPosition().getFile().getName() : "Unknown Source"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/4a3efad6e00824e5814b9c8f571c9c98aad40281/open-pdf-sign/src/main/java/org/openpdfsign/Signer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4a3efad6e00824e5814b9c8f571c9c98aad40281/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4a3efad6e00824e5814b9c8f571c9c98aad40281/open-pdf-sign/src/main/java/org/openpdfsign/Signer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4a3efad6e00824e5814b9c8f571c9c98aad40281/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to ensure they are parsed and attached
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to strictly preserve unrelated formatting/whitespace
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new CertificationPermissionProcessor());

        try {
            System.out.println("Starting DSS Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}