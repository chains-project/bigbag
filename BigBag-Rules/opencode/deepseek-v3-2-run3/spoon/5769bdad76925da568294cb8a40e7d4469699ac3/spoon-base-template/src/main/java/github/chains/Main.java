package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generic transformation rule for fixing breaking changes where a public field
 * has been made private and replaced with a getter method.
 * 
 * This rule transforms field accesses like `object.field` to method calls like
 * `object.getField()`.
 * 
 * Configuration: Modify the FIELD_TO_GETTER_MAP to specify which field accesses
 * should be transformed to which getter methods.
 */
public class Main {
    
    /**
     * Map of field references to getter method names.
     * Key: Fully qualified field reference (e.g., "org.kohsuke.github.GHCompare.status")
     * Value: Getter method name (e.g., "getStatus")
     */
    private static final Map<String, String> FIELD_TO_GETTER_MAP = new HashMap<>();
    
    static {
        // Configure the transformations here
        FIELD_TO_GETTER_MAP.put("org.kohsuke.github.GHCompare.status", "getStatus");
        // Add more transformations as needed for other breaking changes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Transformation rules:");
        for (Map.Entry<String, String> entry : FIELD_TO_GETTER_MAP.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue() + "()");
        }
        
        try {
            int transformedFiles = applyTransformation(dir);
            System.out.println("Transformation completed successfully! Transformed " + transformedFiles + " file(s).");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformation(Path sourceDir) throws IOException {
        final int[] transformedFiles = {0};
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    boolean transformed = transformFile(file);
                    if (transformed) {
                        transformedFiles[0]++;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return transformedFiles[0];
    }
    
    private static boolean transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        String originalContent = content;
        
        // For each field-to-getter mapping, replace field access with method call
        for (Map.Entry<String, String> entry : FIELD_TO_GETTER_MAP.entrySet()) {
            String fullyQualifiedField = entry.getKey();
            String className = fullyQualifiedField.substring(0, fullyQualifiedField.lastIndexOf('.'));
            String fieldName = fullyQualifiedField.substring(fullyQualifiedField.lastIndexOf('.') + 1);
            String getterName = entry.getValue();
            
            // More specific pattern: match instances where we access the field on an object
            // Pattern: .fieldName (but not preceded by "class" or other keywords)
            // We need to be careful to not match things like "Status status = git.status()"
            // A better approach: look for patterns like ").fieldName" or ".fieldName" preceded by a word character
            // Actually, let's use a more sophisticated approach with regex groups
            
            // Pattern: (\w+)\.status\b where \w+ should ideally be checked if it's of type GHCompare
            // But we can't do type checking with simple regex
            // Instead, we'll use a simpler but more careful replacement
            
            // Replace ".status" when it's likely a field access (not method call)
            // Look for ".status" not followed by "("
            String pattern = "(?<!\\.)\\." + fieldName + "(?!\\s*\\()";
            String replacement = "." + getterName + "()";
            
            content = content.replaceAll(pattern, replacement);
        }
        
        if (!content.equals(originalContent)) {
            Files.write(file, content.getBytes());
            System.out.println("Transformed: " + file);
            return true;
        }
        
        return false;
    }
}