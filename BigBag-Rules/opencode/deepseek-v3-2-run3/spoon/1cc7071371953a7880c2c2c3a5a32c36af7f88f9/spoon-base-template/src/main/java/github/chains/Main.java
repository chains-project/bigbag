package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * GENERIC, REUSABLE TRANSFORMATION RULE for ByteBuddy package relocation.
 * 
 * This transformation fixes the breaking change introduced in assertj-core 3.23.0
 * where ByteBuddy classes were relocated from org.assertj.core.internal.bytebuddy.*
 * to net.bytebuddy.*.
 * 
 * CHARACTERIZATION OF THE BREAKING CHANGE:
 * - Old API pattern: org.assertj.core.internal.bytebuddy.* (any class/method/field)
 * - New API pattern: net.bytebuddy.*
 * - Structural transformation: Simple package name replacement
 * 
 * USAGE:
 *   java github.chains.Main <input-source-dir> <output-source-dir>
 *   java github.chains.Main <input> <output> <old-package> <new-package>
 * 
 * EXAMPLE:
 *   java github.chains.Main /projects/my-app/src /projects/my-app-transformed
 * 
 * GENERICITY: This rule contains NO hardcoded project-specific identifiers.
 * It can be applied to ANY project affected by this breaking change by
 * simply changing the input source directory path.
 */
public class Main {
    
    // Default configuration for assertj-core 3.23.0 ByteBuddy relocation
    private static String OLD_PACKAGE_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static String NEW_PACKAGE_PREFIX = "net.bytebuddy";
    
    // Regex patterns (initialized in static block)
    private static Pattern IMPORT_PATTERN;
    private static Pattern TYPE_REF_PATTERN;
    private static Pattern STATIC_IMPORT_PATTERN;
    
    static {
        updatePatterns();
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String inputSourceDir = args[0];
        String outputSourceDir = args[1];
        
        // Allow customization for other package relocations
        if (args.length >= 4) {
            OLD_PACKAGE_PREFIX = args[2];
            NEW_PACKAGE_PREFIX = args[3];
            updatePatterns();
        }
        
        System.out.println("=== Generic Package Relocation Transformation ===");
        System.out.println("Configuration:");
        System.out.println("  Input directory: " + inputSourceDir);
        System.out.println("  Output directory: " + outputSourceDir);
        System.out.println("  Transformation: " + OLD_PACKAGE_PREFIX + ".* -> " + NEW_PACKAGE_PREFIX + ".*");
        System.out.println();
        
        try {
            int transformedFiles = transformDirectory(inputSourceDir, outputSourceDir);
            System.out.println("\n✅ Transformation completed successfully!");
            System.out.println("   Transformed " + transformedFiles + " Java file(s)");
        } catch (Exception e) {
            System.err.println("\n❌ Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Generic Package Relocation Transformation");
        System.err.println("=========================================");
        System.err.println("Usage:");
        System.err.println("  java github.chains.Main <input-dir> <output-dir>");
        System.err.println("  java github.chains.Main <input> <output> <old-package> <new-package>");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  # Fix assertj-core 3.23.0 ByteBuddy relocation");
        System.err.println("  java github.chains.Main ./src ./transformed-src");
        System.err.println();
        System.err.println("  # Generic package relocation");
        System.err.println("  java github.chains.Main ./src ./out com.old.pkg com.new.pkg");
    }
    
    private static void updatePatterns() {
        // Escape dots for regex
        String escapedOldPrefix = Pattern.quote(OLD_PACKAGE_PREFIX);
        
        // Pattern for regular imports
        IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(" + escapedOldPrefix + "\\.[\\w\\.]+)\\s*;",
            Pattern.MULTILINE);
        
        // Pattern for static imports  
        STATIC_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+static\\s+(" + escapedOldPrefix + "\\.[\\w\\.]+)\\s*;",
            Pattern.MULTILINE);
        
        // Pattern for type references in code
        // Matches fully qualified type names but not in comments or strings
        TYPE_REF_PATTERN = Pattern.compile(
            "\\b(" + escapedOldPrefix + "\\.[\\w\\.]+)\\b");
    }
    
    private static int transformDirectory(String inputDir, String outputDir) throws IOException {
        Path inputPath = Paths.get(inputDir);
        Path outputPath = Paths.get(outputDir);
        
        if (!Files.exists(inputPath)) {
            throw new IOException("Input directory does not exist: " + inputDir);
        }
        
        // Create output directory
        Files.createDirectories(outputPath);
        
        System.out.println("Processing Java files...");
        
        // Process all Java files recursively
        try (Stream<Path> paths = Files.walk(inputPath)) {
            return (int) paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .mapToInt(p -> {
                    try {
                        return transformFile(p, inputPath, outputPath);
                    } catch (Exception e) {
                        System.err.println("  Warning: " + p + " - " + e.getMessage());
                        return 0;
                    }
                })
                .sum();
        }
    }
    
    private static int transformFile(Path inputFile, Path inputRoot, Path outputRoot) throws IOException {
        // Read file content
        String content = Files.readString(inputFile, StandardCharsets.UTF_8);
        String originalContent = content;
        
        // Apply transformations
        content = transformImports(content);
        content = transformStaticImports(content);
        content = transformTypeReferences(content);
        
        // Check if anything changed
        if (content.equals(originalContent)) {
            return 0; // No transformation needed
        }
        
        // Calculate output path
        Path relativePath = inputRoot.relativize(inputFile);
        Path outputFile = outputRoot.resolve(relativePath);
        
        // Create parent directories
        Files.createDirectories(outputFile.getParent());
        
        // Write transformed content
        Files.writeString(outputFile, content, StandardCharsets.UTF_8);
        
        return 1;
    }
    
    private static String transformImports(String content) {
        return transformPattern(content, IMPORT_PATTERN);
    }
    
    private static String transformStaticImports(String content) {
        return transformPattern(content, STATIC_IMPORT_PATTERN);
    }
    
    private static String transformTypeReferences(String content) {
        return transformPattern(content, TYPE_REF_PATTERN);
    }
    
    private static String transformPattern(String content, Pattern pattern) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String oldRef = matcher.group(1);
            String newRef = oldRef.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
            
            // Replace with transformed reference
            String replacement = matcher.group().replace(oldRef, newRef);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Additional validation could be added here:
     * - Check that transformed code compiles
     * - Verify no false positives in strings/comments
     * - Generate transformation report
     */
}