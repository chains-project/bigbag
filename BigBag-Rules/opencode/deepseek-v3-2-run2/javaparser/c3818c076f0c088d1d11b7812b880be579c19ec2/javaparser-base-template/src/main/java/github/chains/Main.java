package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    
    /**
     * Generic transformation rule for migrating javax.validation to jakarta.validation.
     * This handles the breaking change in Hibernate Validator 6.0+ where the
     * package namespace changed from javax to jakarta.
     * 
     * Transformation pattern:
     * - Old API: javax.validation.* (any class or import under javax.validation)
     * - New API: jakarta.validation.* (same class name, different package prefix)
     * - Transformation: Replace "javax.validation" with "jakarta.validation" in imports,
     *   fully-qualified type names, and any other references.
     */
    
    /**
     * Processes a single Java source file.
     */
    private static void processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Perform the transformation
        String transformedContent = content.replace("javax.validation", "jakarta.validation");
        
        // Only write back if changes were made
        if (!content.equals(transformedContent)) {
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(transformedContent);
            }
        }
    }
    
    /**
     * Finds all Java source files in a directory recursively.
     */
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        
        try (Stream<Path> stream = Files.walk(startDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(path -> path.toString().endsWith(".java"))
                  .forEach(javaFiles::add);
        }
        
        return javaFiles;
    }
    
    /**
     * Checks if a file contains javax.validation references.
     */
    private static boolean containsValidationReferences(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return content.contains("javax.validation");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.err.println("Example: java Main /workspace/nem");
            System.err.println("");
            System.err.println("This transformation rule migrates javax.validation to jakarta.validation");
            System.err.println("to handle the breaking change in Hibernate Validator 6.0+.");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            System.out.println("Processing source directory: " + sourceDir);
            System.out.println("Transformation: javax.validation -> jakarta.validation");
            System.out.println("Breaking change: Hibernate Validator 6.0+ migration from javax to jakarta namespace");
            System.out.println("");
            
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to scan");
            
            // First, identify files that need transformation
            List<Path> filesToTransform = new ArrayList<>();
            for (Path javaFile : javaFiles) {
                try {
                    if (containsValidationReferences(javaFile)) {
                        filesToTransform.add(javaFile);
                    }
                } catch (Exception e) {
                    System.err.println("Error scanning file " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("Found " + filesToTransform.size() + " files containing javax.validation references");
            System.out.println("");
            
            // Apply transformation to identified files
            int successCount = 0;
            int errorCount = 0;
            
            for (Path javaFile : filesToTransform) {
                try {
                    System.out.println("Transforming: " + sourceDir.relativize(javaFile));
                    processFile(javaFile);
                    successCount++;
                } catch (Exception e) {
                    System.err.println("Error transforming file " + javaFile + ": " + e.getMessage());
                    errorCount++;
                }
            }
            
            System.out.println("");
            System.out.println("Summary:");
            System.out.println("  Total Java files scanned: " + javaFiles.size());
            System.out.println("  Files needing transformation: " + filesToTransform.size());
            System.out.println("  Successfully transformed: " + successCount);
            System.out.println("  Errors: " + errorCount);
            System.out.println("");
            System.out.println("Transformation completed!");
            System.out.println("");
            System.out.println("Note: This is a generic transformation rule that can be applied to");
            System.out.println("any Java project affected by the javax.validation -> jakarta.validation");
            System.out.println("migration in Hibernate Validator 6.0+.");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}