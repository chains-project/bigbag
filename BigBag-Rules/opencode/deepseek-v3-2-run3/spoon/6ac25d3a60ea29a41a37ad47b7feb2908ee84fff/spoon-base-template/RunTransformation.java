import java.io.File;

public class RunTransformation {
    public static void main(String[] args) {
        // Run the Spoon transformation on myfaces-tobago project
        String sourceDir = "/workspace/myfaces-tobago";
        String outputDir = "/workspace/myfaces-tobago-transformed";
        
        System.out.println("Running Jakarta Servlet error attribute transformation...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        try {
            // Create output directory
            new File(outputDir).mkdirs();
            
            // Run the transformation
            github.chains.Main.main(new String[]{sourceDir, outputDir});
            
            System.out.println("\nTransformation completed!");
            System.out.println("Original files remain in: " + sourceDir);
            System.out.println("Transformed files written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error running transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}