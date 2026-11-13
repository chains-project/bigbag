package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Generic Spoon transformation for logback-classic API migration to version 1.4.5
 * This tool identifies and fixes breaking changes in logback usage across Maven projects
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Running logback migration transformation on: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "_fixed");
        
        // Register our processor
        launcher.addProcessor(new LogbackMigrationProcessor());
        
        // Process the source code
        CtModel model = launcher.buildModel();
        
        // Write the transformed code back
        launcher.setSourceOutputDirectory(sourceDir + "_fixed");
        launcher.process();
        
        System.out.println("Transformation complete. Fixed files are in " + sourceDir + "_fixed");
    }
}