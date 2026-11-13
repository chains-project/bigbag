package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation rule for fixing JasperReports API compatibility issues.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        launcher.buildModel();
        
        System.out.println("Spoon transformation initialized for: " + sourceDirectory);
        System.out.println("Transformation would be applied here...");
    }
}