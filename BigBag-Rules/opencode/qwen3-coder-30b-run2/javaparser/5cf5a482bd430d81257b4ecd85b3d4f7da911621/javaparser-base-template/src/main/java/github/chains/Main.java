package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        // The source directory to process (should be configurable)
        String sourceDirectory = "/workspace/jakartaee-mvc-sample/src";
        
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))) {
            // Only process Java files
            Stream<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"));
            
            // Process each file
            long count = javaFiles.peek(Main::processFile).count();
            
            System.out.println("Transformation completed. Processed " + count + " files.");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file content
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            
            // Replace javax.mvc with jakarta.mvc in the content
            String updatedContent = content
                .replace("javax.mvc", "jakarta.mvc")
                .replace("import javax.mvc", "import jakarta.mvc")
                // Also replace javax.ws.rs with jakarta.ws.rs
                .replace("javax.ws.rs", "jakarta.ws.rs")
                .replace("import javax.ws.rs", "import jakarta.ws.rs");
            
            // Write back the modified content
            Files.writeString(filePath, updatedContent, StandardCharsets.UTF_8);
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}