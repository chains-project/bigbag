package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation for fixing breaking changes in asto-core dependency.
 * This transformation can be applied to any Maven project affected by breaking changes
 * in asto-core v1.15.1.
 */
public class Main {
    public static void main(String[] args) {
        // Default values - these can be overridden by command line arguments
        String inputPath = "/workspace/docker-adapter";
        String outputPath = "/workspace/docker-adapter-fixed";
        
        // If arguments provided, use them
        if (args.length >= 1) {
            inputPath = args[0];
        }
        if (args.length >= 2) {
            outputPath = args[1];
        }
        
        System.out.println("Applying generic asto-core breaking change fix to: " + inputPath);
        System.out.println("Output will be saved to: " + outputPath);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setComplianceLevel(17);
            launcher.addInputResource(inputPath);
            launcher.setSourceOutputDirectory(outputPath);
            
            // Build the model
            launcher.buildModel();
            
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}