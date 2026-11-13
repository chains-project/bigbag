package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * A simpler transformation that directly modifies Java source files
 * by replacing the old package name with the new one.
 * 
 * This is a generic, reusable transformation for any package relocation.
 */
public class SimpleFileTransformer {
    
    // Configuration - these could be made parameters for a more generic solution
    private static final String OLD_PACKAGE = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_PACKAGE = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java SimpleFileTransformer <sourceDir> <outputDir>");
            System.err.println("Example: java SimpleFileTransformer /path/to/project/src /path/to/transformed/src");
            System.err.println("");
            System.err.println("This transformation fixes the package relocation:");
            System.err.println("  FROM: " + OLD_PACKAGE);
            System.err.println("  TO:   " + NEW_PACKAGE);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Starting package relocation transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Transforming: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
        
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(outputDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Create output directory if it doesn't exist
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        
        // Process all Java files
        int filesProcessed = 0;
        int replacementsMade = 0;
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         boolean fileChanged = processJavaFile(javaFile, sourcePath, outputPath);
                         if (fileChanged) {
                             System.out.println("  Processed: " + sourcePath.relativize(javaFile));
                         }
                     } catch (IOException e) {
                         System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                     }
                 });
        }
        
        System.out.println("\nTransformation completed!");
        System.out.println("Files processed: " + filesProcessed);
        System.out.println("Replacements made: " + replacementsMade);
    }
    
    private static boolean processJavaFile(Path javaFile, Path sourceRoot, Path outputRoot) throws IOException {
        // Read the file content
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Count replacements for this file
        int countBefore = countOccurrences(content, OLD_PACKAGE);
        
        // Perform the replacement
        content = content.replace(OLD_PACKAGE, NEW_PACKAGE);
        
        // Check if any changes were made
        if (content.equals(originalContent)) {
            // No changes, copy file as-is
            Path relativePath = sourceRoot.relativize(javaFile);
            Path outputFile = outputRoot.resolve(relativePath);
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, content);
            return false;
        }
        
        // Changes were made, write the transformed file
        int countAfter = countOccurrences(content, OLD_PACKAGE);
        int replacements = countBefore - countAfter;
        
        Path relativePath = sourceRoot.relativize(javaFile);
        Path outputFile = outputRoot.resolve(relativePath);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, content);
        
        System.out.println("  -> Fixed " + replacements + " occurrence(s) in " + sourceRoot.relativize(javaFile));
        return true;
    }
    
    private static int countOccurrences(String content, String search) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
    
    /**
     * This method demonstrates how to make the transformation more generic.
     * It could be extended to handle multiple package relocations or
     * read configuration from a file.
     */
    private static class PackageRelocation {
        final String oldPackage;
        final String newPackage;
        
        PackageRelocation(String oldPackage, String newPackage) {
            this.oldPackage = oldPackage;
            this.newPackage = newPackage;
        }
        
        String apply(String content) {
            return content.replace(oldPackage, newPackage);
        }
        
        boolean affects(String content) {
            return content.contains(oldPackage);
        }
    }
}