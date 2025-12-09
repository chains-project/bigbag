package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ThriftRefactoring {

    /**
     * Processor to handle the relocation of TFramedTransport.
     * 
     * Change: org.apache.thrift.transport.TFramedTransport 
     *      -> org.apache.thrift.transport.layered.TFramedTransport
     */
    public static class TFramedTransportProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS = "org.apache.thrift.transport.TFramedTransport";
        private static final String NEW_CLASS = "org.apache.thrift.transport.layered.TFramedTransport";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Filter out references that are not part of the AST we are modifying (e.g., implicit references)
            if (!candidate.isParentInitialized() || candidate.isImplicit()) {
                return false;
            }

            // 2. Check Qualified Name (Robust for NoClasspath if imports exist)
            // We use string comparison to avoid ClassNotFoundException in NoClasspath mode
            String qName = candidate.getQualifiedName();
            return OLD_CLASS.equals(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Logic: Create a reference to the new class location and replace the old one.
            // This handles imports, variable declarations, 'new' instantiations, etc.
            
            // Create the new type reference
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_CLASS);
            
            // Preserve generic arguments if any (though TFramedTransport is usually non-generic)
            newTypeRef.setActualTypeArguments(candidate.getActualTypeArguments());

            // Replace the old reference with the new one
            candidate.replace(newTypeRef);
            
            CtElement parent = newTypeRef.getParent();
            String position = (parent != null && parent.getPosition().isValidPosition()) 
                ? " at line " + parent.getPosition().getLine() 
                : "";
                
            System.out.println("Migrated TFramedTransport to layered package" + position);
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/867e69e208ff59d1f8baae7ed41d3e163a51bc65/singer/singer/src/main/java/com/pinterest/singer/utils/SimpleThriftLogger.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/867e69e208ff59d1f8baae7ed41d3e163a51bc65/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/867e69e208ff59d1f8baae7ed41d3e163a51bc65/singer/singer/src/main/java/com/pinterest/singer/utils/SimpleThriftLogger.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/867e69e208ff59d1f8baae7ed41d3e163a51bc65/attempt_1/transformed");

        // ==========================================
        // CRITICAL: PRESERVE FORMATTING CONFIGURATION
        // ==========================================
        
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter for high-fidelity source reproduction
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (defensive processing without full libs)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new TFramedTransportProcessor());

        System.out.println("Starting Thrift Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}