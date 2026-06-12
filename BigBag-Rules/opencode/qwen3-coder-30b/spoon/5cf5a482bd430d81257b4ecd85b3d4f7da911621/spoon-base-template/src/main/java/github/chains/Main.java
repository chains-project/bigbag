package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        String sourceDir = "/workspace/jakartaee-mvc-sample/src/main/java";
        
        try {
            // Process all Java files in the directory
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation complete - all javax.mvc references updated to jakarta.mvc");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            
            // Replace javax.mvc with jakarta.mvc in imports and references
            String updatedContent = content
                .replaceAll("import javax\\.mvc\\.(.*?);", "import jakarta.mvc.$1;")
                .replaceAll("javax\\.mvc\\.(\\w+)", "jakarta.mvc.$1");
            
            // Write back to file if changes were made
            if (!content.equals(updatedContent)) {
                Files.write(filePath, updatedContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}