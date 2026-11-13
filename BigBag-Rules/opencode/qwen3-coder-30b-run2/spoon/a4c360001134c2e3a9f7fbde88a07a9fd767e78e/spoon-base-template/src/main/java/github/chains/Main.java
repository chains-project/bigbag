package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Simple utility to fix FilterBuilder.apply() calls in Java files
 * This fixes the specific breaking change from org.reflections 0.9.11 to 0.10.2
 */
public class Main {
    public static void main(String[] args) {
        String sourcePath = "/workspace/gauge-java";
        
        try {
            // Process all Java files
            Files.walk(Paths.get(sourcePath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
            
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean changed = false;
            
            // Direct replacement approach - look for specific patterns
            // Pattern 1: new FilterBuilder().include(...).apply(s) => new FilterBuilder().include(...).test(s)
            // We need to be careful to match the whole statement
            
            // First, let's find the exact lines that have the apply calls
            String[] lines = content.split("\n");
            StringBuilder newContent = new StringBuilder();
            
            for (String line : lines) {
                // Find lines containing FilterBuilder.apply and replace appropriately
                if (line.contains(".apply(") && line.contains("FilterBuilder")) {
                    // Replace .apply(s) with .test(s) - this is the exact fix needed
                    line = line.replace(".apply(", ".test(");
                    changed = true;
                }
                newContent.append(line).append("\n");
            }
            
            if (changed) {
                Files.write(filePath, newContent.toString().getBytes());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}