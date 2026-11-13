package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic text-based transformation to fix breaking changes in asto-core dependency.
 * Specifically addresses the removal of Storages class in favor of StoragesLoader.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Applying Storages.newStorage transformation to: " + sourceDir);
        
        try {
            // Walk through all Java files in the directory
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
                
            System.out.println("Transformation complete.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void transformFile(java.nio.file.Path path) {
        try {
            String content = new String(Files.readAllBytes(path));
            String originalContent = content;
            
            // Pattern to match: new Storages().newStorage(...)
            // This is the specific case we encountered in YamlPolicyFactory.java
            // Replace it with new StoragesLoader().newObject(...)
            content = content.replaceAll(
                "new\\s+Storages\\(\\)\\.newStorage\\(([^)]+)\\)",
                "new StoragesLoader().newObject($1)"
            );
            
            // Remove any old imports of Storages
            if (!content.equals(originalContent)) {
                // Remove import statement for Storages
                content = content.replaceAll("import\\s+com\\.artipie\\.asto\\.factory\\.Storages;\\s*", "");
                // Add import for StoragesLoader
                content = content.replaceAll(
                    "(import\\s+com\\.amihaiemil\\.eoyaml\\.Yaml;\\s*import\\s+com\\.artipie\\.asto\\.blocking\\.BlockingStorage;\\s*)",
                    "$1import com.artipie.asto.factory.StoragesLoader;"
                );
                
                Files.write(path, content.getBytes());
                System.out.println("Modified: " + path);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }
}