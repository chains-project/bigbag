package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Simple Spoon transformation to fix package renames in Maven projects.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        
        // Enable compilation of the processed code
        launcher.getEnvironment().setShouldCompile(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build model and get factory
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Fix import statements from develop.p2p.lib to tokyo.peya.lib
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (imp.getReference() != null && 
                imp.getReference().toString().startsWith("develop.p2p.lib")) {
                // Replace with new reference
                String oldReference = imp.getReference().toString();
                String newReference = oldReference.replace("develop.p2p.lib", "tokyo.peya.lib");
                
                // Create and set new import reference
                CtTypeReference<?> newRef = factory.Type().createReference(newReference);
                imp.setReference(newRef);
            }
        }
        
        // Process the model - we'll just build it now to make sure it compiles
        try {
            launcher.process();
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}