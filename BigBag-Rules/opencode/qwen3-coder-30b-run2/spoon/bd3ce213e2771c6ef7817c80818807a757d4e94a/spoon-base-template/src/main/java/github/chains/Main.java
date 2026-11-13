package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix Jackson 2.13 breaking change
 * where StreamWriteException was moved from com.fasterxml.jackson.core.exc
 * to com.fasterxml.jackson.core
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and fix imports
        model.getElements(element -> element instanceof CtImport)
              .forEach(importElement -> {
                  CtImport ctImport = (CtImport) importElement;
                  String importedType = ctImport.getReference().toString();
                  
                  // Check if this is the old StreamWriteException import and fix it
                  if (importedType.equals("com.fasterxml.jackson.core.exc.StreamWriteException")) {
                      // We'll replace the import reference with the new qualified name
                      Factory factory = launcher.getFactory();
                      ctImport.setReference(factory.Type().createReference("com.fasterxml.jackson.core.StreamWriteException"));
                  }
              });
        
        // Write the modified code back to the files
        launcher.process();
        System.out.println("Jackson StreamWriteException fix completed successfully on " + sourceDirectory);
    }
}