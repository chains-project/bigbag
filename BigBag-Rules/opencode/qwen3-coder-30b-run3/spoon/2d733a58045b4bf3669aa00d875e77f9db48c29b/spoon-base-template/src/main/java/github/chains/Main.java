package github.chains;

import spoon.Launcher;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;

import java.io.File;
import java.util.Arrays;

/**
 * Main class to run the Flyway API transformation
 */
public class Main {
    public static void main(String[] args) {
        // Create a launcher for Spoon
        Launcher launcher = new Launcher();
        
        // Set the source folders to process
        launcher.addInputResource("./src/main/java");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("./target/generated-sources");
        
        // Add our processor
        launcher.addProcessor(new FlywayFixTransformer());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Flyway API transformation completed.");
    }
}