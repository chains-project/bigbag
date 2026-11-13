package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

/**
 * Generic Spoon transformation to fix Maven dependency tree API breaking change
 * Specifically replaces Maven31DependencyGraphBuilder with DefaultDependencyGraphBuilder
 */
public class Main {
    public static void main(String[] args) {
        // Simple test to show the tool works
        System.out.println("Spoon transformation tool for Maven dependency tree API fix");
        System.out.println("Usage: java -cp spoon-base-template-1.0-SNAPSHOT.jar github.chains.Main <source_directory>");
        
        if (args.length == 0) {
            System.out.println("No source directory specified");
            return;
        }
        
        // Configure the launcher with proper arguments
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Add source directory
        launcher.addInputResource(args[0]);
        
        // Add the processor
        launcher.addProcessor(new DependencyGraphFixProcessor());
        
        // Build and process model
        CtModel model = launcher.buildModel();
        launcher.process();
        
        System.out.println("Transformation completed on " + args[0]);
    }
}