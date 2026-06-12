package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A generic, reusable transformation for fixing package relocation issues in Java projects.
 * 
 * This transformer can handle:
 * 1. Import statement updates
 * 2. Fully-qualified type reference updates
 * 3. Simple type name references (when imports are updated)
 * 
 * Usage: java GenericPackageRelocator <sourceDir> <outputDir> <oldPackage> <newPackage>
 */
public class GenericPackageRelocator {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: java GenericPackageRelocator <sourceDir> <outputDir> <oldPackage> <newPackage>");
            System.err.println("");
            System.err.println("Example: java GenericPackageRelocator /path/to/src /path/to/transformed");
            System.err.println("         com.github.javaparser.printer.PrettyPrinterConfiguration");
            System.err.println("         com.github.javaparser.printer.configuration.PrettyPrinterConfiguration");
            System.err.println("");
            System.err.println("This transformation fixes package relocation issues by updating:");
            System.err.println("  1. Import statements");
            System.err.println("  2. Fully-qualified type references in code");
            System.err.println("  3. Static imports");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String oldPackage = args[2];
        String newPackage = args[3];
        
        System.out.println("Starting generic package relocation transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Transforming: " + oldPackage + " -> " + newPackage);
        
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
        List<ProcessedFile> processedFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         ProcessedFile result = processJavaFile(javaFile, sourcePath, outputPath, oldPackage, newPackage);
                         processedFiles.add(result);
                         if (result.wasModified()) {
                             System.out.println("  Modified: " + sourcePath.relativize(javaFile) + 
                                               " (" + result.getReplacements() + " replacement(s))");
                         }
                     } catch (IOException e) {
                         System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                     }
                 });
        }
        
        // Print summary
        System.out.println("\nTransformation completed!");
        System.out.println("Files processed: " + processedFiles.size());
        System.out.println("Files modified: " + processedFiles.stream().filter(ProcessedFile::wasModified).count());
        System.out.println("Total replacements: " + processedFiles.stream().mapToInt(ProcessedFile::getReplacements).sum());
        
        // Show detailed breakdown
        System.out.println("\nDetailed breakdown:");
        for (ProcessedFile file : processedFiles) {
            if (file.wasModified()) {
                System.out.println("  " + file.getRelativePath() + ": " + file.getReplacements() + " replacement(s)");
            }
        }
    }
    
    private static ProcessedFile processJavaFile(Path javaFile, Path sourceRoot, Path outputRoot, 
                                                 String oldPackage, String newPackage) throws IOException {
        // Read the file content
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Count occurrences before replacement
        int countBefore = countOccurrences(content, oldPackage);
        
        if (countBefore == 0) {
            // No changes needed, copy file as-is
            Path relativePath = sourceRoot.relativize(javaFile);
            Path outputFile = outputRoot.resolve(relativePath);
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, content);
            return new ProcessedFile(sourceRoot.relativize(javaFile).toString(), false, 0);
        }
        
        // Perform the replacement
        content = content.replace(oldPackage, newPackage);
        
        // Also handle the simple class name in import statements
        // If we have "import old.package.ClassName;" we need to change it to "import new.package.ClassName;"
        // The simple replacement above handles this, but we also need to handle cases where
        // the class is referenced by simple name after import
        
        // Write the transformed file
        Path relativePath = sourceRoot.relativize(javaFile);
        Path outputFile = outputRoot.resolve(relativePath);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, content);
        
        return new ProcessedFile(sourceRoot.relativize(javaFile).toString(), true, countBefore);
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
     * Helper class to track file processing results
     */
    private static class ProcessedFile {
        private final String relativePath;
        private final boolean modified;
        private final int replacements;
        
        public ProcessedFile(String relativePath, boolean modified, int replacements) {
            this.relativePath = relativePath;
            this.modified = modified;
            this.replacements = replacements;
        }
        
        public String getRelativePath() {
            return relativePath;
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        public int getReplacements() {
            return replacements;
        }
    }
    
    /**
     * Example of how to use this transformer programmatically
     */
    public static void transformProject(Path sourceDir, Path outputDir, String oldPackage, String newPackage) throws IOException {
        String[] args = {
            sourceDir.toString(),
            outputDir.toString(),
            oldPackage,
            newPackage
        };
        main(args);
    }
}