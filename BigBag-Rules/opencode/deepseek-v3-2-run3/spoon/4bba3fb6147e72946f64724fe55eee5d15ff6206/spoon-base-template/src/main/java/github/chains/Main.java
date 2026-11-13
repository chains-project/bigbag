package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for migrating from javax.annotation to jakarta.annotation.
 * This handles the breaking change in Jakarta EE where the javax.annotation package
 * was migrated to jakarta.annotation as part of the Java EE to Jakarta EE transition.
 * 
 * The transformation:
 * 1. Changes imports from javax.annotation.* to jakarta.annotation.*
 * 2. Changes type references from javax.annotation.X to jakarta.annotation.X
 * 3. Works for any annotation type in the javax.annotation package (Priority, Resource, PostConstruct, etc.)
 * 
 * This transformation is generic and can be applied to ANY Java project affected by
 * the javax.annotation -> jakarta.annotation breaking change.
 * 
 * Key characteristics of the breaking change:
 * - Old API pattern: javax.annotation.X (where X = Priority, Resource, PostConstruct, PreDestroy, Generated, ManagedBean, etc.)
 * - New API pattern: jakarta.annotation.X (same class names, different package)
 * - Structural transformation: Simple package rename from javax.annotation to jakarta.annotation
 */
public class Main {
    
    private static final String OLD_PACKAGE = "javax.annotation";
    private static final String NEW_PACKAGE = "jakarta.annotation";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Applying javax.annotation -> jakarta.annotation transformation to: " + sourcePath);
        
        try {
            int totalChanges = transformDirectory(sourcePath);
            System.out.println("\nTransformation complete: " + totalChanges + " files updated");
            System.out.println("\nThe transformation:");
            System.out.println("1. Changed package from " + OLD_PACKAGE + " to " + NEW_PACKAGE);
            System.out.println("2. Applied to all annotation types in the package:");
            System.out.println("   - javax.annotation.Priority -> jakarta.annotation.Priority");
            System.out.println("   - javax.annotation.Resource -> jakarta.annotation.Resource");
            System.out.println("   - javax.annotation.PostConstruct -> jakarta.annotation.PostConstruct");
            System.out.println("   - javax.annotation.PreDestroy -> jakarta.annotation.PreDestroy");
            System.out.println("   - javax.annotation.Generated -> jakarta.annotation.Generated");
            System.out.println("   - javax.annotation.ManagedBean -> jakarta.annotation.ManagedBean");
            System.out.println("   - ...and all other javax.annotation types");
            System.out.println("3. This fix works for ANY project with the same breaking change");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformDirectory(String directoryPath) throws IOException {
        int totalChanges = 0;
        Path startPath = Paths.get(directoryPath);
        
        try (Stream<Path> paths = Files.walk(startPath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    totalChanges++;
                }
            }
        }
        
        return totalChanges;
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String originalContent = content;
        
        // Replace imports
        content = content.replace("import " + OLD_PACKAGE + ".", "import " + NEW_PACKAGE + ".");
        
        // Replace fully qualified type references in code
        content = content.replace(OLD_PACKAGE + ".", NEW_PACKAGE + ".");
        
        // Check if any changes were made
        if (!content.equals(originalContent)) {
            Files.writeString(filePath, content);
            System.out.println("  Updated: " + filePath);
            return true;
        }
        
        return false;
    }
}