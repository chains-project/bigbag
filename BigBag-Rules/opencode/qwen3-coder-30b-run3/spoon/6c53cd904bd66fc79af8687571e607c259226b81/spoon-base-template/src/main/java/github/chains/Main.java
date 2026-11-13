package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

/**
 * Generic transformation rule to fix breaking Struts2 dependency changes.
 * Specifically handles the change from:
 *   org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
 * to:
 *   org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try {
            // Process all Java files recursively
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation completed successfully.");
            System.out.println("Modified files are in: " + sourceDirectory);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            // Read the entire file content
            String content = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
            
            // Pattern to match the old class reference
            String oldClassPattern = "org\\.apache\\.struts2\\.dispatcher\\.ng\\.filter\\.StrutsPrepareAndExecuteFilter";
            String newClassPattern = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";
            
            // Replace in content
            String updatedContent = content.replaceAll(oldClassPattern, newClassPattern);
            
            // If content was changed, write back to file
            if (!content.equals(updatedContent)) {
                Files.write(javaFile, updatedContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Updated: " + javaFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
}