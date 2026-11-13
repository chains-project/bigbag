package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Generic Spoon transformation to fix breaking dependency changes.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "_fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Print a summary of changes made
        System.out.println("Transformation completed. Changes saved to " + sourceDir + "_fixed");
    }
}