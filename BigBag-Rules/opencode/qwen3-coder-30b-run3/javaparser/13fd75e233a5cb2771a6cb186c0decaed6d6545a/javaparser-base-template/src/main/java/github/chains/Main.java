package github.chains;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Simple text-based transformation to fix Hamcrest StringContains and StringStartsWith constructor calls.
 * 
 * Breaking change: Hamcrest 2.0+ changed constructors from (boolean, String) to (String)
 * This transformation looks for calls to:
 * - new StringContains(true, "literal")
 * - new StringContains(false, "literal")
 * - new StringStartsWith(true, "literal")
 * - new StringStartsWith(false, "literal")
 * 
 * And replaces them with:
 * - new StringContains("literal")
 * - new StringStartsWith("literal")
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing files in: " + sourceDir);
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path path) {
        try {
            String content = Files.readString(path);
            
            // Multiple patterns to catch various spacing and formatting issues
            String[] patterns = {
                "new\\s+(StringContains|StringStartsWith)\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)",
                "new\\s+(StringContains|StringStartsWith)\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)"
            };
            
            String replacedContent = content;
            boolean changed = false;
            
            // Try to match and replace all patterns
            for (String pattern : patterns) {
                String newContent = replacedContent.replaceAll(pattern, "new $1($3)");
                if (!replacedContent.equals(newContent)) {
                    changed = true;
                    replacedContent = newContent;
                }
            }
            
            // Write back to file if modified
            if (changed) {
                Files.writeString(path, replacedContent);
                System.out.println("Fixed: " + path);
            } else {
                System.out.println("No changes needed: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
}
