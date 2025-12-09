package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for Apache Thrift: TFramedTransport removed/moved.
 * 
 * Breaking Change:
 * - CLASS org.apache.thrift.transport.TFramedTransport [REMOVED]
 * 
 * Migration Strategy:
 * - In newer Thrift versions (0.14+), TFramedTransport was moved to the 'layered' subpackage.
 * - Action: Replace usages of 'org.apache.thrift.transport.TFramedTransport' 
 *   with 'org.apache.thrift.transport.layered.TFramedTransport'.
 */
public class ThriftMigration {

    public static class TransportUpdateProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        private static final String OLD_CLASS_QNAME = "org.apache.thrift.transport.TFramedTransport";
        private static final String NEW_CLASS_QNAME = "org.apache.thrift.transport.layered.TFramedTransport";
        private static final String OLD_SIMPLE_NAME = "TFramedTransport";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive Check: Ensure candidate is valid
            if (candidate == null) return false;
            
            // 1. Check Simple Name (Fast filter)
            if (!OLD_SIMPLE_NAME.equals(candidate.getSimpleName())) {
                return false;
            }

            // 2. Check Qualified Name (Robust check for NoClasspath)
            // Note: In NoClasspath, getQualifiedName() attempts to resolve via imports.
            // If resolution fails, it might return the simple name, so we check strictly for the old package
            // or if it matches the simple name and likely needs migration based on context (omitted for safety).
            String qName = candidate.getQualifiedName();
            
            // We strictly check the fully qualified name to avoid false positives, 
            // but also handle cases where it might already be the new type.
            if (NEW_CLASS_QNAME.equals(qName)) {
                return false; // Already migrated
            }

            // Match exact old qualified name
            return OLD_CLASS_QNAME.equals(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Transformation: Replace the reference with the new package location.
            // We create a new reference to ensure imports are updated correctly by Spoon/Sniper.
            
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_CLASS_QNAME);
            
            // Preserve generic arguments if any exist on the old reference
            if (!candidate.getActualTypeArguments().isEmpty()) {
                newTypeRef.setActualTypeArguments(candidate.getActualTypeArguments());
            }

            // Replace the old reference in the AST
            candidate.replace(newTypeRef);
            
            CtElement parent = newTypeRef.getParent();
            String position = (parent != null && parent.getPosition().isValidPosition()) 
                ? String.valueOf(parent.getPosition().getLine()) 
                : "unknown";
                
            System.out.println("Migrated TFramedTransport at line " + position);
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/867e69e208ff59d1f8baae7ed41d3e163a51bc65/singer/singer/src/main/java/com/pinterest/singer/reader/ThriftReader.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/867e69e208ff59d1f8baae7ed41d3e163a51bc65/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/867e69e208ff59d1f8baae7ed41d3e163a51bc65/singer/singer/src/main/java/com/pinterest/singer/reader/ThriftReader.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/867e69e208ff59d1f8baae7ed41d3e163a51bc65/attempt_1/transformed");

        // =========================================================
        // CRITICAL CONFIGURATION: Preservation & NoClasspath Safety
        // =========================================================
        
        // 1. Enable comments to ensure Javadoc/inline comments aren't lost
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable NoClasspath mode (Defensive typing)
        launcher.getEnvironment().setNoClasspath(true);
        
        // 3. Force Sniper Printer (Preserve formatting/indentation)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Apply the processor
        launcher.addProcessor(new TransportUpdateProcessor());

        try {
            System.out.println("Starting Thrift Migration...");
            launcher.run();
            System.out.println("Migration complete. Check output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during migration:");
            e.printStackTrace();
        }
    }
}