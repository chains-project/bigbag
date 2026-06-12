package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon.jar github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        try {
            // Process all Java files in the directory
            Path dir = Paths.get(sourceDirectory);
            if (!Files.exists(dir)) {
                System.err.println("Source directory does not exist: " + sourceDirectory);
                System.exit(1);
            }
            
            // Create a pattern to find javax.mvc references
            Pattern pattern = Pattern.compile("javax\\.mvc", Pattern.LITERAL);
            
            // Process all Java files
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            
            // Replace javax.mvc with jakarta.mvc
            String newContent = content.replace("javax.mvc", "jakarta.mvc");
            
            // Only write if content actually changed
            if (!content.equals(newContent)) {
                Files.write(filePath, newContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}