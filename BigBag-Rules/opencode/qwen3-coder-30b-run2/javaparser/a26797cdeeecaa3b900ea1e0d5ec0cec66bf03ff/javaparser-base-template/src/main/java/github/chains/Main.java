/*
 * Generic transformation rule for fixing breaking changes in Logback dependency updates
 * 
 * This is a template that can be used to fix breaking API changes in Logback 1.4.8
 * Specifically targets removal or changes to methods like setIncludeCallerData and isIncludeCallerData
 * 
 * Usage: java -cp target/classes github.chains.Main /path/to/project/src
 */

package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourcePath);
            System.exit(1);
        }
        
        System.out.println("Processing Java files in: " + sourcePath);
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
        System.out.println("Transformation complete.");
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            // Read file content
            String content = new String(Files.readAllBytes(javaFile));
            
            // Apply transformations to fix logback API breaking changes
            String transformedContent = transformLogbackApi(content);
            
            // Write back the modified file if there were changes
            if (!content.equals(transformedContent)) {
                Files.write(javaFile, transformedContent.getBytes());
                System.out.println("Updated: " + javaFile);
            } else {
                System.out.println("No changes needed: " + javaFile);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transform logback API calls to be compatible with version 1.4.8
     * This handles breaking changes related to includeCallerData methods in AsyncAppender
     */
    private static String transformLogbackApi(String content) {
        // Remove calls to setIncludeCallerData(boolean) - commonly removed in newer versions
        content = content.replaceAll(
            "(AsyncAppender\\.setIncludeCallerData\\(.*?\\);)",
            "// Removed deprecated setIncludeCallerData call"
        );
        
        // Remove calls to isIncludeCallerData() - commonly removed in newer versions  
        content = content.replaceAll(
            "(AsyncAppender\\.isIncludeCallerData\\(\\);)",
            "// Removed deprecated isIncludeCallerData call"
        );
        
        // Handle other common patterns that might be broken in logback 1.4.8
        content = content.replaceAll(
            "(\\.setIncludeCallerData\\(.*?\\);)",
            "// Removed deprecated includeCallerData call"
        );
        
        // Handle the case where it's a standalone line (not ending with semicolon)
        content = content.replaceAll(
            "(AsyncAppender\\.setIncludeCallerData\\(.*?\\))",
            "// Removed deprecated setIncludeCallerData call"
        );
        
        content = content.replaceAll(
            "(AsyncAppender\\.isIncludeCallerData\\(\\))",
            "// Removed deprecated isIncludeCallerData call"
        );
        
        return content;
    }
}