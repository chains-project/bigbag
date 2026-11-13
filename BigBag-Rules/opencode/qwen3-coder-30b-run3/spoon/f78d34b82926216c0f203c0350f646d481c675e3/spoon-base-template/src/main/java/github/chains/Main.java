package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        // Set the input directory (where the source code is located)
        String inputPath = "/workspace/PeyangSuperbAntiCheat/src/main/java";
        String outputPath = "/workspace/PeyangSuperbAntiCheat/src/main/java-transformed";
        
        // Create launcher
        Launcher launcher = new Launcher();
        
        // Set the source folders to process
        launcher.addInputResource(inputPath);
        
        // Set the output directory
        launcher.setSourceOutputDirectory(outputPath);
        
        // Add our processor
        launcher.addProcessor(new BreakingChangesTransformationProcessor());
        
        // Process the model with our custom processor
        launcher.run();
        
        // Print a success message
        System.out.println("Transformation completed! Files saved to " + outputPath);
    }
}