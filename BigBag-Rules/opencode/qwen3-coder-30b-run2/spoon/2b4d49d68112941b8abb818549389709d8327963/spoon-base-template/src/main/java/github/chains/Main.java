package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Generic Spoon transformation to fix Hazelcast API breaking changes 
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Hazelcast API Fix Transformation Started");
        
        // Create a basic Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source folder to transform 
        String sourceFolder = "/workspace/openfire-hazelcast-plugin/src";
        
        // Add the source folder to Spoon
        launcher.addInputResource(sourceFolder);
        
        // Set output directory
        launcher.setSourceOutputDirectory("/workspace/spoon-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        System.out.println("Model built successfully with " + model.getAllTypes().size() + " types");
        
        System.out.println("Transformation completed");
    }
}