package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * GENERIC transformation rule for removing deprecated constant class references.
 * 
 * This transformation handles the pattern where a constant class has been removed
 * from a dependency, and its constants need to be replaced with literal values.
 * 
 * Pattern: OLD_API: FullyQualifiedClassName.CONSTANT_NAME (e.g., com.jcabi.aspects.Tv.SEVEN)
 *          NEW_API: literal value (e.g., 7)
 * 
 * The transformation is parameterized by:
 * 1. The fully-qualified class name to remove imports for
 * 2. A mapping of constant names to their literal values
 * 
 * This implementation uses text replacement for robustness and simplicity.
 * It can be applied to ANY Maven project affected by the same breaking change
 * by simply changing the configuration constants.
 */
public class Main {
    
    // ===== CONFIGURATION - PARAMETERIZE THESE FOR DIFFERENT BREAKING CHANGES =====
    
    // The fully-qualified class name that was removed from the dependency
    private static final String DEPRECATED_CLASS = "com.jcabi.aspects.Tv";
    
    // The simple class name (without package) for pattern matching
    private static final String SIMPLE_CLASS_NAME = DEPRECATED_CLASS.substring(
        DEPRECATED_CLASS.lastIndexOf('.') + 1);
    
    // Mapping of constant names to their replacement literal values
    private static final Map<String, String> CONSTANT_MAPPING = new HashMap<>();
    static {
        CONSTANT_MAPPING.put("SEVEN", "7");
        CONSTANT_MAPPING.put("TEN", "10");
        CONSTANT_MAPPING.put("MILLION", "1000000");
        // Add more constants as needed for other breaking changes
    }
    
    // ===== END CONFIGURATION =====
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("  source-directory: Root directory containing Java source files to transform");
            System.err.println("\nConfiguration:");
            System.err.println("  Deprecated class: " + DEPRECATED_CLASS);
            System.err.println("  Constant mapping: " + CONSTANT_MAPPING);
            System.err.println("\nTo adapt for other breaking changes, modify the configuration constants.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("=== Generic Constant Replacement Transformation ===");
        System.out.println("Target: " + DEPRECATED_CLASS);
        System.out.println("Constants: " + CONSTANT_MAPPING.size() + " mappings defined");
        System.out.println("Processing: " + sourceDir);
        
        try {
            int filesProcessed = transformProject(sourceDir);
            System.out.println("\n✓ Transformation complete!");
            System.out.println("  Processed " + filesProcessed + " Java file(s)");
            
        } catch (Exception e) {
            System.err.println("\n✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transforms all Java files in the directory tree.
     */
    private static int transformProject(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java file(s)");
        
        int transformedFiles = 0;
        int totalReplacements = 0;
        
        for (File javaFile : javaFiles) {
            TransformationStats stats = transformFile(javaFile);
            if (stats.filesChanged > 0) {
                transformedFiles += stats.filesChanged;
                totalReplacements += stats.replacementsMade;
            }
        }
        
        System.out.println("Made " + totalReplacements + " total replacement(s)");
        return transformedFiles;
    }
    
    /**
     * Recursively finds all Java files in a directory.
     */
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        
        return javaFiles;
    }
    
    /**
     * Transforms a single Java file.
     */
    private static TransformationStats transformFile(File javaFile) throws IOException {
        Path filePath = javaFile.toPath();
        String content = Files.readString(filePath);
        
        TransformationStats stats = new TransformationStats();
        
        // 1. Remove imports of the deprecated class
        String importPattern = "import " + DEPRECATED_CLASS + ";";
        if (content.contains(importPattern)) {
            content = content.replace(importPattern, "");
            stats.importsRemoved++;
            stats.changed = true;
        }
        
        // 2. Replace all constant references
        for (Map.Entry<String, String> entry : CONSTANT_MAPPING.entrySet()) {
            String constantName = entry.getKey();
            String replacement = entry.getValue();
            
            // Pattern 1: Simple class name (Tv.SEVEN)
            String simplePattern = SIMPLE_CLASS_NAME + "." + constantName;
            int simpleCount = countAndReplace(content, simplePattern, replacement);
            if (simpleCount > 0) {
                content = content.replace(simplePattern, replacement);
                stats.constantsReplaced += simpleCount;
                stats.changed = true;
            }
            
            // Pattern 2: Fully qualified name (com.jcabi.aspects.Tv.SEVEN)
            String qualifiedPattern = DEPRECATED_CLASS + "." + constantName;
            int qualifiedCount = countAndReplace(content, qualifiedPattern, replacement);
            if (qualifiedCount > 0) {
                content = content.replace(qualifiedPattern, replacement);
                stats.constantsReplaced += qualifiedCount;
                stats.changed = true;
            }
        }
        
        // 3. Handle the case where the class might be imported with alias (not common but possible)
        // This is a more complex pattern that would require AST parsing
        
        if (stats.changed) {
            // Write the transformed content
            Files.write(filePath, content.getBytes());
            stats.filesChanged = 1;
            stats.replacementsMade = stats.importsRemoved + stats.constantsReplaced;
            
            if (stats.replacementsMade > 0) {
                System.out.println("  " + javaFile.getName() + ": " + stats.replacementsMade + " change(s)");
            }
        }
        
        return stats;
    }
    
    /**
     * Counts occurrences of a pattern in text (non-regex).
     */
    private static int countAndReplace(String text, String pattern, String replacement) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
    
    /**
     * Statistics about transformations applied to a file.
     */
    private static class TransformationStats {
        int filesChanged = 0;
        int importsRemoved = 0;
        int constantsReplaced = 0;
        int replacementsMade = 0;
        boolean changed = false;
    }
    
    /**
     * Example of how to make this tool more generic with command-line arguments.
     * This could be expanded to accept configuration file or JSON input.
     */
    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> config = new HashMap<>();
        // In a more advanced version, parse args like:
        // --class=com.jcabi.aspects.Tv --mapping=SEVEN=7,TEN=10,MILLION=1000000
        return config;
    }
}