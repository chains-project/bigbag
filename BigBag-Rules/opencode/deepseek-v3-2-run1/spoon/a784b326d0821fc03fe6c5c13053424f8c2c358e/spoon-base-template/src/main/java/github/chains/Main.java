package github.chains;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

/**
 * Generic transformation rule for javax.validation -> jakarta.validation migration.
 * 
 * This transformation handles the breaking change in Hibernate Validator 8.0+
 * where the package namespace changed from javax.validation to jakarta.validation.
 * 
 * Breaking Change Characterization:
 * - Old API pattern: javax.validation.* (e.g., javax.validation.Valid)
 * - New API pattern: jakarta.validation.* (e.g., jakarta.validation.Valid)
 * - Structural transformation: Package namespace replacement
 * 
 * The transformation is applicable to ANY Java project affected by this
 * dependency update, not just the @nem/ project.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("\nGeneric transformation for javax.validation -> jakarta.validation migration");
            System.err.println("This fixes the breaking change in Hibernate Validator 8.0+");
            System.exit(1);
        }

        Path startDir = Paths.get(args[0]);
        System.out.println("Applying javax.validation -> jakarta.validation transformation to: " + startDir.toAbsolutePath());
        System.out.println("This is a generic fix for the Hibernate Validator 8.0+ breaking change\n");
        
        int filesProcessed = processDirectory(startDir);
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("Processed " + filesProcessed + " Java file(s)");
        System.out.println("\nThe following changes were applied:");
        System.out.println("1. import javax.validation.* -> import jakarta.validation.*");
        System.out.println("2. javax.validation.X -> jakarta.validation.X (fully-qualified references)");
        System.out.println("3. @javax.validation.X -> @jakarta.validation.X (annotation references)");
    }
    
    private static int processDirectory(Path startDir) throws IOException {
        final int[] count = {0};
        
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    try {
                        if (processJavaFile(file)) {
                            count[0]++;
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to process file: " + file + " - " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.err.println("Failed to access file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        
        return count[0];
    }
    
    private static boolean processJavaFile(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file));
        String originalContent = content;
        
        // Apply transformations in order of specificity
        
        // 1. Replace import statements (most common case)
        content = content.replace("import javax.validation", "import jakarta.validation");
        
        // 2. Replace fully-qualified annotation references
        content = content.replace("@javax.validation.", "@jakarta.validation.");
        
        // 3. Replace other fully-qualified type references
        // This handles cases like: javax.validation.ConstraintValidatorContext
        // But avoids double-replacing already processed imports
        content = content.replace("javax.validation.", "jakarta.validation.");
        
        // Check if any changes were made
        if (!content.equals(originalContent)) {
            Files.write(file, content.getBytes());
            System.out.println("✓ Fixed: " + file);
            return true;
        }
        
        return false;
    }
}