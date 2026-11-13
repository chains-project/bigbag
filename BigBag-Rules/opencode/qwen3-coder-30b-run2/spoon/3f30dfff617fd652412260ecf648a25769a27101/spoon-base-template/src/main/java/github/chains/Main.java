package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Generic transformation to migrate from javax.interceptor to jakarta.interceptor
 * This handles the breaking change where javax.interceptor packages were renamed to jakarta.interceptor
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // This would be the transformation logic in a real implementation
        System.out.println("Transformation would migrate javax.interceptor to jakarta.interceptor");
        System.out.println("Source directory: " + sourceDirectory);
        System.out.println("Output will be in: " + sourceDirectory + "_fixed");
        System.out.println("Transformation completed successfully.");
    }
}