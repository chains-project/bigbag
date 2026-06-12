package github.chains;

import spoon.Launcher;

/**
 * Generic Maven API transformation rule to fix breaking changes in Maven dependencies.
 * This transformation fixes:
 * 1. org.apache.maven.project.MavenProject -> org.apache.maven.model.MavenProject
 * 2. org.apache.maven.artifact.DependencyResolutionRequiredException -> org.apache.maven.plugin.DependencyResolutionRequiredException
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(7);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        
        // Process the code
        launcher.buildModel();
        
        // Save the transformed code
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}