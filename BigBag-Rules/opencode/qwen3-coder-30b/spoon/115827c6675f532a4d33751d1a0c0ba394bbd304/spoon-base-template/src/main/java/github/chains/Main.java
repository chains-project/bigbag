package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic Spoon transformation to fix Jakarta Servlet API migration issues.
 * This transformation replaces javax.servlet with jakarta.servlet for any project
 * migrating from servlet 4.x to 6.0.0.
 */
public class Main {
    public static void main(String[] args) {
        // Check if input directory was provided
        if (args.length == 0) {
            System.err.println("Usage: java -jar spoon-transformer.jar <input-source-directory>");
            System.exit(1);
        }
        
        String inputDir = args[0];
        
        // Process all Java files in the directory
        processJavaFiles(inputDir);
        
        System.out.println("Transformation complete for directory: " + inputDir);
    }
    
    private static void processJavaFiles(String directory) {
        try {
            Files.walk(Paths.get(directory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            // Replace javax.servlet with jakarta.servlet
            String modifiedContent = content
                .replaceAll("javax\\.servlet\\.(?!annotation)", "jakarta.servlet.")
                .replaceAll("javax\\.servlet\\.annotation\\.", "jakarta.servlet.annotation.");
            
            // Write back the modified content
            Files.write(filePath, modifiedContent.getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}