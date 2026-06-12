package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/processed");
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all imports that contain net.lingala.zip4j.core
        List<CtImport> imports = factory.getModel().getElements(
            importRef -> importRef.getReference() != null && 
                importRef.getReference().toString().contains("net.lingala.zip4j.core")
        );
        
        // Transform the imports
        for (CtImport importRef : imports) {
            CtReference reference = importRef.getReference();
            if (reference != null) {
                String importPath = reference.toString();
                if (importPath.contains("net.lingala.zip4j.core")) {
                    // Replace the import path
                    String newImportPath = importPath.replace("net.lingala.zip4j.core", "net.lingala.zip4j");
                    CtTypeReference<?> newReference = factory.Type().createReference(newImportPath);
                    importRef.setReference(newReference);
                }
            }
        }
        
        // Output the modified model
        launcher.setSourceOutputDirectory(sourceDirectory + "/processed");
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
    }
}