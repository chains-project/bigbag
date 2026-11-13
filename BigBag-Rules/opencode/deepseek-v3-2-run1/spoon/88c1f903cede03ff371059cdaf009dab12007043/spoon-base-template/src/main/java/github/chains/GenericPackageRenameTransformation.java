package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * A generic transformation for renaming Java package imports and type references.
 * This can be used to fix breaking changes in dependencies where packages are renamed.
 * 
 * Usage: java GenericPackageRenameTransformation <source-dir> <old-package> <new-package>
 * Example: java GenericPackageRenameTransformation /path/to/src net.lingala.zip4j.core net.lingala.zip4j
 */
public class GenericPackageRenameTransformation {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java GenericPackageRenameTransformation <source-directory> <old-package> <new-package>");
            System.err.println("Example: java GenericPackageRenameTransformation /path/to/src net.lingala.zip4j.core net.lingala.zip4j");
            System.err.println("");
            System.err.println("This transformation fixes package rename breaking changes in dependencies.");
            System.err.println("It updates both import statements and fully-qualified type references.");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        String oldPackage = args[1];
        String newPackage = args[2];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Renaming package: " + oldPackage + " -> " + newPackage);
        
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        // Walk through all Java files
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    processJavaFile(file, oldPackage, newPackage);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Error accessing file: " + file + ": " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Transformation complete!");
    }
    
    private static void processJavaFile(Path file, String oldPackage, String newPackage) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> newLines = new ArrayList<>();
        boolean modified = false;
        
        for (String line : lines) {
            String newLine = line;
            
            // Check if this line contains the old package
            if (line.contains(oldPackage)) {
                // Replace all occurrences of old package with new package
                newLine = line.replace(oldPackage, newPackage);
                
                if (!newLine.equals(line)) {
                    System.out.println("Updated " + file.getFileName() + ": " + line.trim() + " -> " + newLine.trim());
                    modified = true;
                }
            }
            
            newLines.add(newLine);
        }
        
        if (modified) {
            Files.write(file, newLines, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("  -> Saved changes to " + file);
        }
    }
}