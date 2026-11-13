package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation for fixing zip4j 2.x breaking API changes.
 * 
 * Breaking Change: ZipFile class moved from net.lingala.zip4j.core.ZipFile 
 *                  to net.lingala.zip4j.ZipFile
 * 
 * This transformation:
 * 1. Updates import statements from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
 * 2. Works on any Java project affected by this package relocation
 * 3. Uses configurable old and new package patterns for generality
 */
public class Main {
    
    // Configuration: Old and new package patterns
    // These can be parameterized for different breaking changes
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";
    private static final String NEW_PACKAGE = "net.lingala.zip4j";
    private static final String CLASS_NAME = "ZipFile";
    
    // Map of old fully-qualified names to new ones
    // Can be extended for multiple class relocations
    private static final Map<String, String> RELOCATION_MAP = new HashMap<>();
    
    static {
        RELOCATION_MAP.put(OLD_PACKAGE + "." + CLASS_NAME, NEW_PACKAGE + "." + CLASS_NAME);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Transformation: " + OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
        
        try {
            int filesProcessed = 0;
            int importsUpdated = 0;
            
            // Process all Java files recursively
            processDirectory(Paths.get(sourceDir), filesProcessed, importsUpdated);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory, int filesProcessed, int importsUpdated) throws IOException {
        File[] files = directory.toFile().listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file.toPath(), filesProcessed, importsUpdated);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file.toPath(), filesProcessed, importsUpdated);
            }
        }
    }
    
    private static void processJavaFile(Path file, int filesProcessed, int importsUpdated) throws IOException {
        filesProcessed++;
        String content = Files.readString(file);
        String originalContent = content;
        
        // Apply all relocation patterns
        for (Map.Entry<String, String> entry : RELOCATION_MAP.entrySet()) {
            String oldFqn = entry.getKey();
            String newFqn = entry.getValue();
            
            // Pattern for import statements
            String importPattern = "import\\s+" + oldFqn.replace(".", "\\.") + "\\s*;";
            
            // Replace import statements
            content = content.replaceAll(importPattern, "import " + newFqn + ";");
        }
        
        // Check if any changes were made
        if (!content.equals(originalContent)) {
            importsUpdated++;
            System.out.println("Updated imports in: " + file);
            Files.writeString(file, content);
        }
        
        // Print summary
        System.out.println("\nTransformation complete!");
        System.out.println("Files processed: " + filesProcessed);
        System.out.println("Imports updated: " + importsUpdated);
    }
}