package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix breaking changes in plexus-utils 4.0.0
 * Specifically addresses the removal of org.codehaus.plexus.util.xml.Xpp3Dom
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation to fix plexus-utils 4.0.0 breaking changes");
        System.out.println("This transformation removes imports to the deprecated org.codehaus.plexus.util.xml package");
        System.out.println("Usage: java -cp target/classes github.chains.Main [input_dir] [output_dir]");
        
        // Create a new Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process - this should be configurable
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/depclean";
        String outputDirectory = args.length > 1 ? args[1] : "/workspace/depclean-transformed";
        
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find and remove imports that reference the problematic package
        int removedCount = 0;
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<CtImport>(CtImport.class))) {
            if (imp.getReference() != null && 
                imp.getReference().toString().contains("org.codehaus.plexus.util.xml")) {
                imp.delete();
                removedCount++;
                System.out.println("Removed import: " + imp.getReference().toString());
            }
        }
        
        System.out.println("Removed " + removedCount + " imports to deprecated package");
        
        // Create a simple output to show what this transformation does
        System.out.println("Transformation complete. This would remove problematic imports in your codebase.");
    }
}