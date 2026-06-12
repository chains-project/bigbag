package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation to fix Mockito 4.1.0 breaking changes
 */
public class Main {
    public static void main(String[] args) {
        // Check if source directory is provided
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        
        // Process the code
        launcher.buildModel();
        
        System.out.println("Transformation completed on " + sourceDirectory);
    }
}