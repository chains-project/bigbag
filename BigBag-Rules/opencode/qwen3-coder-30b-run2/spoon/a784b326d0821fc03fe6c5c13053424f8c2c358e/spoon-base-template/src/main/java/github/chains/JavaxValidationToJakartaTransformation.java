package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

public class JavaxValidationToJakartaTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java JavaxValidationToJakartaTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-processed");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Apply the processor
        ValidationApiMigrationProcessor processor = new ValidationApiMigrationProcessor();
        model.processWith(processor);
        
        // Print processing info
        System.out.println("Processing completed for directory: " + sourceDirectory);
        System.out.println("Output saved to: " + sourceDirectory + "-processed");
    }
}