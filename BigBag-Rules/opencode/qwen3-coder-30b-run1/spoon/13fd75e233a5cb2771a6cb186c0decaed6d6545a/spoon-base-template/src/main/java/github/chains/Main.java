package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        String outputDirectory = args.length > 1 ? args[1] : "/workspace/docker-adapter-transformed";
        
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDirectory));
            
            // Process all Java files in the source directory
            Files.walk(Paths.get(sourceDirectory))
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
                 
            System.out.println("Transformation complete. Processed files saved to: " + outputDirectory);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path path) {
        try {
            String content = new String(Files.readAllBytes(path));
            String transformedContent = transformContent(content);
            
            // Write to output directory with same structure
            Path outputPath = Paths.get(
                "/workspace/docker-adapter-transformed",
                path.toString().substring("/workspace/docker-adapter".length())
            );
            
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, transformedContent.getBytes());
            
        } catch (IOException e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }
    
    private static String transformContent(String content) {
        // Fix StringContains constructor calls with boolean parameter
        // Pattern: new StringContains(true, "message") or new StringContains(false, "message")
        content = content.replaceAll(
            "new StringContains\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\"|\\w+)\\s*\\)",
            "new StringContains($2)"
        );
        
        // Fix StringStartsWith constructor calls with boolean parameter
        // Pattern: new StringStartsWith(true, "message") or new StringStartsWith(false, "message")
        content = content.replaceAll(
            "new StringStartsWith\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\"|\\w+)\\s*\\)",
            "new StringStartsWith($2)"
        );
        
        return content;
    }
}