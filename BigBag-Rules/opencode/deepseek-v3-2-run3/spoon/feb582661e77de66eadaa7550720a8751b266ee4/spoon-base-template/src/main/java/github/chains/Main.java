package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for Liquibase 4.8.0 breaking changes.
 * 
 * This transformation handles:
 * 1. Class renames (e.g., StringUtils -> StringUtil)
 * 2. Static factory method removal (e.g., ExecutorService.getInstance() -> Scope.getCurrentScope().getSingleton(...))
 * 
 * The transformation is parameterized and can be applied to any project.
 */
public class Main {
    
    // Configuration: Map of old class names to new class names
    private static final Map<String, String> CLASS_RENAMES = Map.of(
        "liquibase.util.StringUtils", "liquibase.util.StringUtil"
    );
    
    // Configuration: Map of static factory methods to their replacements
    // Key: fullyQualifiedClassName.methodName
    // Value: replacement pattern (use {class} as placeholder for the class)
    private static final Map<String, String> STATIC_METHOD_REPLACEMENTS = Map.of(
        "liquibase.executor.ExecutorService.getInstance", 
        "Scope.getCurrentScope().getSingleton({class}.class)"
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // Walk through all Java files
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
        
        System.out.println("Transformation completed.");
    }
    
    private static void transformFile(Path file) {
        try {
            String content = Files.readString(file);
            String originalContent = content;
            
            // Apply class renames
            for (Map.Entry<String, String> entry : CLASS_RENAMES.entrySet()) {
                String oldClass = entry.getKey();
                String newClass = entry.getValue();
                
                // Extract simple names for unqualified replacement
                String oldSimpleName = oldClass.substring(oldClass.lastIndexOf('.') + 1);
                String newSimpleName = newClass.substring(newClass.lastIndexOf('.') + 1);
                
                // Replace imports
                content = content.replaceAll(
                    "import " + oldClass + ";",
                    "import " + newClass + ";"
                );
                
                // Replace qualified usage
                content = content.replaceAll(
                    Pattern.quote(oldClass),
                    newClass
                );
                
                // Replace unqualified usage (only if we have the import)
                if (content.contains("import " + newClass + ";")) {
                    content = content.replaceAll(
                        "\\b" + Pattern.quote(oldSimpleName) + "\\b",
                        newSimpleName
                    );
                }
            }
            
            // Apply static method replacements
            for (Map.Entry<String, String> entry : STATIC_METHOD_REPLACEMENTS.entrySet()) {
                String[] parts = entry.getKey().split("\\.");
                String className = String.join(".", Arrays.copyOf(parts, parts.length - 1));
                String methodName = parts[parts.length - 1];
                
                String replacement = entry.getValue();
                replacement = replacement.replace("{class}", className);
                
                // Pattern: ClassName.methodName()
                String pattern = Pattern.quote(className) + "\\." + Pattern.quote(methodName) + "\\s*\\(\\s*\\)";
                
                content = content.replaceAll(pattern, replacement);
                
                // Add import for Scope if needed
                if (replacement.contains("Scope.") && !content.contains("import liquibase.Scope;")) {
                    content = addImport(content, "liquibase.Scope");
                }
            }
            
            if (!content.equals(originalContent)) {
                Files.writeString(file, content);
                System.out.println("Updated: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    private static String addImport(String content, String importStatement) {
        // Find the package statement
        int packageIndex = content.indexOf("package ");
        if (packageIndex >= 0) {
            int endOfPackage = content.indexOf(";", packageIndex);
            if (endOfPackage >= 0) {
                String before = content.substring(0, endOfPackage + 1);
                String after = content.substring(endOfPackage + 1);
                return before + "\nimport " + importStatement + ";" + after;
            }
        }
        return content;
    }
}