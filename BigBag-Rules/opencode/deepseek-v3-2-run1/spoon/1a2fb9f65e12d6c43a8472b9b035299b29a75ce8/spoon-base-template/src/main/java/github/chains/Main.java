package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming javax.validation to jakarta.validation in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Add our transformation processor
        launcher.addProcessor(new ValidationTransformer());
        launcher.addProcessor(new ValidationTransformer.ImportTransformer());
        
        try {
            // Build model and apply transformations
            CtModel model = launcher.buildModel();
            
            // Process the model (transformations happen during processing)
            launcher.process();
            
            // Pretty-print the transformed code back to source directory
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("All javax.validation references have been migrated to jakarta.validation.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}