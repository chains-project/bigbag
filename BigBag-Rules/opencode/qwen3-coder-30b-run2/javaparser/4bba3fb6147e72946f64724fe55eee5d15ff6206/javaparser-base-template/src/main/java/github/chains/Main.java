package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic transformation to replace javax.annotation with jakarta.annotation
 * This transformation handles all javax.annotation imports and usages consistently.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path rootPath = Paths.get(sourceDir);
        
        // Process all .java files recursively
        Files.walk(rootPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
        System.out.println("Transformation complete!");
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            String transformedContent = content.replaceAll(
                "import javax\\.annotation\\.(.*?);",
                "import jakarta.annotation.$1;"
            );
            
            // Also handle any remaining references in the code
            transformedContent = transformedContent.replaceAll(
                "javax\\.annotation\\.(.*?)(?=\\s|\\(|\\)|,|;|\\.)",
                "jakarta.annotation.$1"
            );
            
            if (!content.equals(transformedContent)) {
                Files.write(filePath, transformedContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Updated: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Failed to process file: " + filePath + " - " + e.getMessage());
        }
    }
}
