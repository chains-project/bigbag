package github.chains;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation rule for Jakarta EE namespace migration.
 * 
 * BREAKING CHANGE PATTERN ANALYSIS:
 * - Old API pattern: javax.mvc.* (and other javax.* EE packages)
 * - New API pattern: jakarta.mvc.* (and corresponding jakarta.* packages)
 * - Structural transformation: Simple package rename from javax.* to jakarta.*
 * 
 * This transformation handles the Jakarta EE 9+ namespace migration where
 * all javax.* packages were renamed to jakarta.*. The transformation is:
 * 1. Generic: Works for any project affected by this breaking change
 * 2. Reusable: Simply change source and output directory paths
 * 3. Configurable: Package mappings can be easily modified
 * 
 * USAGE:
 *   java Main <source-directory> <output-directory>
 *   
 * EXAMPLE:
 *   java Main /path/to/project/src /path/to/transformed/src
 *   
 * The transformation processes all Java files, replacing javax.* imports
 * and type references with jakarta.* equivalents based on the mapping.
 */
public class Main {
    
    // Configuration: Map of old package prefixes to new package prefixes
    // Users can modify this map to add/remove package transformations
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Jakarta EE 9+ package migrations
        // These transformations handle the javax -> jakarta namespace change
        PACKAGE_MAPPINGS.put("javax.mvc", "jakarta.mvc");
        PACKAGE_MAPPINGS.put("javax.ws.rs", "jakarta.ws.rs");
        PACKAGE_MAPPINGS.put("javax.validation", "jakarta.validation");
        PACKAGE_MAPPINGS.put("javax.annotation", "jakarta.annotation");
        PACKAGE_MAPPINGS.put("javax.enterprise", "jakarta.enterprise");
        PACKAGE_MAPPINGS.put("javax.inject", "jakarta.inject");
        PACKAGE_MAPPINGS.put("javax.ejb", "jakarta.ejb");
        PACKAGE_MAPPINGS.put("javax.persistence", "jakarta.persistence");
        PACKAGE_MAPPINGS.put("javax.servlet", "jakarta.servlet");
        PACKAGE_MAPPINGS.put("javax.security", "jakarta.security");
        PACKAGE_MAPPINGS.put("javax.transaction", "jakarta.transaction");
        PACKAGE_MAPPINGS.put("javax.faces", "jakarta.faces");
        PACKAGE_MAPPINGS.put("javax.batch", "jakarta.batch");
        PACKAGE_MAPPINGS.put("javax.mail", "jakarta.mail");
        PACKAGE_MAPPINGS.put("javax.activation", "jakarta.activation");
        
        // Note: Add additional package mappings as needed for specific projects
        // The pattern is: PACKAGE_MAPPINGS.put("old.package", "new.package");
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-directory> <output-directory>");
            System.err.println("  source-directory: Path to Java source code to transform");
            System.err.println("  output-directory: Path where transformed code will be written");
            System.err.println("\nExample: java Main ./src ./transformed-src");
            System.err.println("\nTransformation handles: javax.* -> jakarta.* (Jakarta EE migration)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Jakarta EE Namespace Migration Transformation");
        System.out.println("==============================================");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println("Package mappings configured: " + PACKAGE_MAPPINGS.size());
        
        try {
            transformProject(sourceDir, outputDir);
            System.out.println("\nTransformation completed successfully!");
            System.out.println("Applied " + PACKAGE_MAPPINGS.size() + " package mappings");
        } catch (Exception e) {
            System.err.println("\nTransformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String outputDir) throws Exception {
        // Validate source directory exists
        if (!Files.exists(Paths.get(sourceDir))) {
            throw new RuntimeException("Source directory does not exist: " + sourceDir);
        }
        
        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(outputDir));
        
        // Walk through all Java files in source directory
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    String content = new String(Files.readAllBytes(path));
                    String transformedContent = transformJavaFile(content);
                    
                    // Preserve directory structure in output
                    String relativePath = Paths.get(sourceDir).relativize(path).toString();
                    java.nio.file.Path outputPath = Paths.get(outputDir, relativePath);
                    Files.createDirectories(outputPath.getParent());
                    Files.write(outputPath, transformedContent.getBytes());
                    
                    System.out.println("✓ " + relativePath);
                } catch (Exception e) {
                    System.err.println("✗ Error processing " + path + ": " + e.getMessage());
                }
            });
    }
    
    /**
     * Transforms a Java file by replacing javax.* packages with jakarta.*
     * based on the configured package mappings.
     */
    private static String transformJavaFile(String content) {
        String transformed = content;
        
        // Apply all package mappings
        for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
            String oldPrefix = entry.getKey();
            String newPrefix = entry.getValue();
            
            // Replace import statements
            transformed = transformed.replace("import " + oldPrefix + ".", "import " + newPrefix + ".");
            
            // Replace fully qualified type references in code
            transformed = transformed.replace(oldPrefix + ".", newPrefix + ".");
            
            // Handle case where import doesn't have a trailing . (single class import)
            transformed = transformed.replace("import " + oldPrefix + ";", "import " + newPrefix + ";");
        }
        
        return transformed;
    }
    
    /**
     * Alternative method for more advanced transformations if needed.
     * This could use Spoon AST manipulation for complex refactorings.
     */
    private static void advancedTransformation(String sourceDir, String outputDir) {
        // This method would use Spoon for AST-based transformations
        // when simple text replacement isn't sufficient.
        // Currently using text-based approach for simplicity and reliability.
        System.out.println("Using text-based transformation (Spoon AST available if needed)");
    }
}