package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.util.List;

public class FlywayFixMain {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Add the source directory to analyze
        launcher.addInputResource("/workspace/nem/nis/src/main/java");
        launcher.addInputResource("/workspace/nem/nis/src/test/java");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Create our processor
        FlywayConstructorFixProcessor processor = new FlywayConstructorFixProcessor();
        
        // Process all classes
        model.processWith(processor);
        
        System.out.println("Flyway constructor detection completed.");
        System.out.println("Check the output files in /tmp/spoon-output");
    }
}