package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

/**
 * Simple file-based transformation for fixing assertj-core 3.23.0 breaking change.
 * 
 * This transformation updates:
 * - Import statements from org.assertj.core.internal.bytebuddy.* to net.bytebuddy.*
 * - Type references in code from org.assertj.core.internal.bytebuddy.* to net.bytebuddy.*
 * - Static imports from org.assertj.core.internal.bytebuddy.* to net.bytebuddy.*
 * 
 * Generic pattern: This can be adapted for any package relocation by changing
 * the OLD_PREFIX and NEW_PREFIX constants.
 */
public class Main {
    
    // Configuration - make these constants to make the transformation generic
    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";
    
    // Additional replacements for common patterns
    private static final Map<String, String> ADDITIONAL_REPLACEMENTS = new HashMap<>();
    static {
        // Add any specific class name changes if needed
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.err.println("Transformation: " + OLD_PREFIX + " -> " + NEW_PREFIX);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Starting transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Replacement: " + OLD_PREFIX + " -> " + NEW_PREFIX);
        
        int fileCount = 0;
        int replacementCount = 0;
        
        // Walk through all Java files
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                    fileCount++;
                    int replacementsInFile = processFile(file);
                    replacementCount += replacementsInFile;
                    
                    if (replacementsInFile > 0) {
                        System.out.println("  Updated " + replacementsInFile + " occurrences in " + 
                                          sourceDir.relativize(file));
                    }
                }
            }
        }
        
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Files processed: " + fileCount);
        System.out.println("Total replacements: " + replacementCount);
        System.out.println("Pattern: " + OLD_PREFIX + " -> " + NEW_PREFIX);
        
        if (replacementCount > 0) {
            System.out.println("\nNote: This transformation handles:");
            System.out.println("  1. Regular imports (import " + OLD_PREFIX + ".ClassName;)");
            System.out.println("  2. Static imports (import static " + OLD_PREFIX + ".ClassName.*;)");
            System.out.println("  3. Type references in code (" + OLD_PREFIX + ".ClassName)");
            System.out.println("  4. Fully qualified names in code");
        } else {
            System.out.println("\nNo replacements were necessary.");
        }
    }
    
    private static int processFile(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file), "UTF-8");
        String originalContent = content;
        
        // Apply main package replacement
        content = content.replace(OLD_PREFIX, NEW_PREFIX);
        
        // Apply any additional specific replacements
        for (Map.Entry<String, String> replacement : ADDITIONAL_REPLACEMENTS.entrySet()) {
            content = content.replace(replacement.getKey(), replacement.getValue());
        }
        
        // Write back only if changed
        if (!content.equals(originalContent)) {
            Files.write(file, content.getBytes("UTF-8"));
            return countReplacements(originalContent, content);
        }
        
        return 0;
    }
    
    private static int countReplacements(String original, String updated) {
        // Simple count of how many times OLD_PREFIX was replaced
        int count = 0;
        int index = 0;
        while ((index = updated.indexOf(NEW_PREFIX, index)) != -1) {
            count++;
            index += NEW_PREFIX.length();
        }
        return count;
    }
}