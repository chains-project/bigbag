package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for migrating from javax.validation to jakarta.validation.
 * 
 * Breaking Change Characterization:
 * - Old API pattern: javax.validation.* (e.g., javax.validation.Validator, javax.validation.constraints.NotNull)
 * - New API pattern: jakarta.validation.* (e.g., jakarta.validation.Validator, jakarta.validation.constraints.NotNull)
 * - Structural transformation: Simple package name replacement from "javax.validation" to "jakarta.validation"
 * 
 * This transformation is applicable to ANY Java project affected by the javax.validation
 * to jakarta.validation namespace change (Java EE to Jakarta EE migration).
 * 
 * Usage: java -jar transformation.jar <source-directory>
 */
public class Main {
    
    /**
     * Transforms javax.validation references to jakarta.validation in Java source files.
     * This handles:
     * 1. Import statements
     * 2. Fully-qualified type references
     * 3. Static imports
     * 4. Comments and string literals (though less common)
     */
    public static void transformFile(Path javaFile) throws IOException {
        boolean fileChanged = false;
        List<String> lines = Files.readAllLines(javaFile);
        List<String> newLines = new ArrayList<>();
        
        for (String line : lines) {
            String originalLine = line;
            String transformedLine = line;
            
            // Transform javax.validation to jakarta.validation
            // Using replaceAll to handle all occurrences in the line
            if (line.contains("javax.validation")) {
                transformedLine = line.replace("javax.validation", "jakarta.validation");
                if (!transformedLine.equals(originalLine)) {
                    fileChanged = true;
                    System.out.println("  Updated: " + originalLine.trim() + " -> " + transformedLine.trim());
                }
            }
            
            newLines.add(transformedLine);
        }
        
        if (fileChanged) {
            Files.write(javaFile, newLines);
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println();
            System.err.println("Transforms javax.validation imports and references to jakarta.validation.");
            System.err.println("This is a generic transformation for the javax.validation -> jakarta.validation");
            System.err.println("namespace migration (Java EE to Jakarta EE).");
            System.err.println();
            System.err.println("Example: java -jar transformation.jar /path/to/your/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("=== javax.validation to jakarta.validation Migration Tool ===");
        System.out.println("Processing source directory: " + sourceDirectory);
        System.out.println();
        
        Path startDir = Paths.get(sourceDirectory);
        if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDirectory);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        
        // Collect all Java files recursively
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Warning: Could not access file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        System.out.println();
        
        int changedFiles = 0;
        int totalChanges = 0;
        
        // Process each Java file
        for (Path javaFile : javaFiles) {
            try {
                // Read file to check if it contains javax.validation
                String content = new String(Files.readAllBytes(javaFile));
                if (content.contains("javax.validation")) {
                    System.out.println("Processing: " + startDir.relativize(javaFile));
                    int beforeChanges = totalChanges;
                    transformFile(javaFile);
                    if (totalChanges > beforeChanges) {
                        changedFiles++;
                    }
                    System.out.println();
                }
            } catch (IOException e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("=== Transformation Summary ===");
        System.out.println("Total Java files scanned: " + javaFiles.size());
        System.out.println("Files modified: " + changedFiles);
        System.out.println("Total changes made: " + totalChanges);
        System.out.println();
        
        if (changedFiles > 0) {
            System.out.println("Transformation complete!");
            System.out.println("Note: You may also need to:");
            System.out.println("1. Update your build dependencies from javax.validation-api to jakarta.validation-api");
            System.out.println("2. Ensure transitive dependencies are compatible with jakarta.validation");
            System.out.println("3. Update any configuration files or deployment descriptors");
        } else {
            System.out.println("No javax.validation references found. No changes were made.");
        }
    }
}