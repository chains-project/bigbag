package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        // Simple text-based transformation to replace JsonBranch with PathCache
        Path projectPath = Paths.get("/workspace/qa-catalogue/src/main/java");
        
        // Find all Java source files in the project
        try (var paths = Files.walk(projectPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
        }
        
        System.out.println("Transformation completed.");
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            String content = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
            
            // Remove JsonBranch imports
            content = content.replaceAll(
                "import de\\.gwdg\\.metadataqa\\.api\\.json\\.JsonBranch;\\s*", "");
            
            // Replace JsonBranch in variable declarations and method signatures 
            content = content.replaceAll("\\bJsonBranch\\b", "de.gwdg.metadataqa.api.model.pathcache.PathCache");
            
            // Write back to file
            Files.write(javaFile, content.getBytes(StandardCharsets.UTF_8));
            
            System.out.println("Processed: " + javaFile);
        } catch (IOException e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
        }
    }
}