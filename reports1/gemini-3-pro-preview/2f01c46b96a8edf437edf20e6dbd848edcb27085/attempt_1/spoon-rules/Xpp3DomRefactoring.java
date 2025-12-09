package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class Xpp3DomRefactoring {

    /**
     * Processor to handle the migration of Xpp3Dom.
     * The class org.codehaus.plexus.util.xml.Xpp3Dom was removed.
     * The standard migration path is usually to org.apache.maven.shared.utils.xml.Xpp3Dom
     * or to update the package if it has moved within the ecosystem.
     * This processor updates the package of the TypeReference.
     */
    public static class Xpp3DomProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS_NAME = "org.codehaus.plexus.util.xml.Xpp3Dom";
        private static final String NEW_PACKAGE_NAME = "org.apache.maven.shared.utils.xml";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Safety Check: Ignore references without a package (e.g. primitives, generics)
            if (candidate.getPackage() == null) {
                return false;
            }

            // 2. Exact Match Check
            // We use getQualifiedName() which works in NoClasspath mode assuming imports are present
            // or fully qualified names are used in the source.
            return OLD_CLASS_NAME.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // 3. Transformation: Update the package of the type reference.
            // This handles both fully qualified usages in code and import statements.
            
            // Check if this reference is part of an import, or usage in code.
            // In Spoon, modifying the package of the reference updates how it is printed.
            
            candidate.setPackage(getFactory().Package().getOrCreate(NEW_PACKAGE_NAME));
            
            // Log the change
            CtElement parent = candidate.getParent();
            int line = (candidate.getPosition().isValidPosition()) 
                ? candidate.getPosition().getLine() 
                : (parent != null && parent.getPosition().isValidPosition() ? parent.getPosition().getLine() : -1);
                
            System.out.println("Refactored Xpp3Dom reference at line " + line);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/2f01c46b96a8edf437edf20e6dbd848edcb27085/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2f01c46b96a8edf437edf20e6dbd848edcb27085/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/2f01c46b96a8edf437edf20e6dbd848edcb27085/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2f01c46b96a8edf437edf20e6dbd848edcb27085/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and robust refactoring
        // 1. Enable comments to preserve license headers and Javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Disable classpath requirement (run on source only)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new Xpp3DomProcessor());

        try {
            System.out.println("Starting Xpp3Dom refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}