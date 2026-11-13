package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic JavaParser transformation template for fixing breaking dependency updates.
 * This transformation removes org.cactoos imports and provides a framework for
 * replacing cactoos usage patterns with standard Java alternatives.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        
        Path dir = Paths.get(sourceDir);
        try {
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file content
            String content = Files.readString(filePath);
            
            // Remove cactoos imports
            content = content.replaceAll("(?m)^import\\s+org\\.cactoos\\.[^;]*;\\s*$", "");
            
            // Write back the modified content
            Files.writeString(filePath, content);
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}