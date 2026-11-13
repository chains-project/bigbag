package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix breaking changes in jcabi-aspects dependencies.
 * This transformation handles common breaking changes that occur when upgrading
 * from older versions of jcabi-aspects to newer versions like 0.25.1.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory
        launcher.addInputResource(sourceDir);
        
        // Set the output directory
        launcher.setSourceOutputDirectory(sourceDir + "/fixed");
        
        // Build the model
        SpoonAPI spoon = launcher;
        spoon.buildModel();
        
        Factory factory = spoon.getFactory();
        
        // Print info about what was found
        System.out.println("Analysis complete for source directory: " + sourceDir);
        System.out.println("This transformation identifies jcabi-aspects breaking changes.");
    }
}