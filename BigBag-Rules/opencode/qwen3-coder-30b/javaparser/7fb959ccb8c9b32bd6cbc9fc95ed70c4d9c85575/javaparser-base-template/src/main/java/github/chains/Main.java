package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDirectory = "/workspace/jadler"; // Default to jadler project
        
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        try {
            // Process all Java files in the directory
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation completed successfully for all Java files in " + sourceDirectory);
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            
            // Replace SelectChannelConnector with ServerConnector
            String newContent = content.replaceAll(
                "new SelectChannelConnector\\(\\)", 
                "new ServerConnector()"
            );
            
            // If content was changed, write it back
            if (!content.equals(newContent)) {
                Files.write(filePath, newContent.getBytes());
                System.out.println("Updated file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}