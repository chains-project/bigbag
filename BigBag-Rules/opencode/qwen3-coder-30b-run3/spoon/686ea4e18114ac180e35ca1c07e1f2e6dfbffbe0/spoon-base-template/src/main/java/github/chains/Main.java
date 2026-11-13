package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Check that we have the necessary arguments
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory
        launcher.addInputResource(sourceDirectory);
        
        // Configure the launcher to process Java files
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process the model to replace javax.ws.rs with jakarta.ws.rs imports
        Factory factory = launcher.getFactory();
        
        // Find all imports that reference javax.ws.rs packages
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        // Iterate through all imports and replace javax.ws.rs with jakarta.ws.rs
        for (CtImport imp : imports) {
            // Check if this import references a javax.ws.rs package
            String importRef = imp.getReference().toString();
            if (importRef.contains("javax.ws.rs")) {
                String newImport = importRef.replace("javax.ws.rs", "jakarta.ws.rs");
                // Create a new import for the new qualified name
                CtImport newImportStmt = factory.createImport(factory.Type().createReference(newImport));
                // Replace the old import with the new one
                imp.replace(newImportStmt);
            }
        }
        
        // Write back the modified sources
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
    }
}