import java.io.File;

public class TestTransformation {
    public static void main(String[] args) {
        // Create output directory
        File outputDir = new File("/workspace/PGS-transformed");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // Run the transformation
        github.chains.Main.main(new String[]{
            "/workspace/PGS/src/main/java",
            "/workspace/PGS-transformed"
        });
    }
}