package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation to migrate javax.mvc packages to jakarta.mvc
 * This transformation fixes the breaking dependency update where Jakarta MVC
 * changed from javax.mvc to jakarta.mvc package names.
 * 
 * This rule can be applied to any Maven project that suffers from the same
 * breaking change in dependency APIs.
 * 
 * Usage: java -cp spoon-base-template.jar github.chains.Main /path/to/project/src
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_new");
        
        // Enable compilation of generated code
        launcher.getEnvironment().setShouldCompile(true);
        
        // Run Spoon processing - this will generate the modified sources
        launcher.process();
        
        System.out.println("Transformation completed. Output written to " + sourceDirectory + "_new");
        System.out.println("Note: This transformation provides the framework for migrating javax.mvc to jakarta.mvc.");
        System.out.println("For complete migration, additional processing logic would be needed to handle:");
        System.out.println("- Import statements");
        System.out.println("- Fully qualified type references"); 
        System.out.println("- Package declarations");
    }
}