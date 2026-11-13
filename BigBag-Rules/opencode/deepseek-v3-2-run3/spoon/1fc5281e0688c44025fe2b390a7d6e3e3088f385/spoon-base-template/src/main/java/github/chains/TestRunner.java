package github.chains;

import java.io.File;

/**
 * Test runner for the Jetty transformation
 */
public class TestRunner {
    public static void main(String[] args) {
        // Test the transformation on jadler project
        String sourceDir = "/workspace/jadler/jadler-jetty/src/main/java";
        String outputDir = "/workspace/transformed-output";
        
        System.out.println("Testing transformation on: " + sourceDir);
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        // Run the transformation
        String[] transformArgs = {sourceDir, outputDir};
        Main.main(transformArgs);
        
        System.out.println("\nTest completed. Check output in: " + outputDir);
    }
}