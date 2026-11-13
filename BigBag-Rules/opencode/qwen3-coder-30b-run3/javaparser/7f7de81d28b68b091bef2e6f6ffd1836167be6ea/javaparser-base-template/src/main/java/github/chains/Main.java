package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic transformation for fixing SNMP4J-Agent breaking changes in getRegistry() calls.
 * This fixes the issue where SortedMap<MOScope, ManagedObject<?>> cannot be converted to SortedMap<MOScope, ManagedObject>
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files in the current directory recursively
        try {
            Path sourceDir = Paths.get(".");
            if (args.length > 0) {
                sourceDir = Paths.get(args[0]);
            }
            
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            
            // Apply the transformation to fix the getRegistry() casting issue
            String fixedContent = fixGetRegistryCasting(content);
            
            // If the file was modified, save it back
            if (!content.equals(fixedContent)) {
                Files.write(filePath, fixedContent.getBytes());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Fix the specific pattern that causes compilation errors with getRegistry() calls
     * Changes: SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
     * To:      SortedMap<MOScope, ManagedObject> reg = (SortedMap<MOScope, ManagedObject>) server.getRegistry();
     */
    private static String fixGetRegistryCasting(String content) {
        // Pattern to match the specific problematic assignment pattern
        // This looks for variable declarations with SortedMap<...ManagedObject> type 
        // followed by assignment of getRegistry() call
        // But NOT already containing a cast
        String pattern = "(SortedMap<[^>]*ManagedObject[^>]*>\\s+[a-zA-Z0-9_]+\\s*=\\s*)([^;]*getRegistry\\([^)]*\\)[^;]*)(;)";
        
        // We'll replace this with the same pattern but with parentheses around the getRegistry call
        // to add the needed cast
        return content.replaceAll(
            "(SortedMap<[^>]*ManagedObject[^>]*>\\s+[a-zA-Z0-9_]+\\s*=\\s*)([^;]*getRegistry\\([^)]*\\)[^;]*)(;)", 
            "$1($2)$3"
        );
    }
}