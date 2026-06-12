package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;

public class Main {
    public static void main(String[] args) {
        // Create a launcher for Spoon
        Launcher launcher = new Launcher();
        
        // Set the source directory
        launcher.addInputResource("/workspace/facebook-java-business-sdk/src/main/java");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        
        // Add our transformation processor
        launcher.addProcessor(new OkioFixTransformation());
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process the model
        launcher.process();
        
        System.out.println("Transformation complete!");
    }
}