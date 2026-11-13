package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtTypeAccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic Spoon transformation to fix javax.mvc -> jakarta.mvc migration issues.
 * This transformation scans all Java source files in the specified directory,
 * and replaces all references to javax.mvc with jakarta.mvc.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Source directory to transform
        String sourceDirectory = "/workspace/jakartaee-mvc-sample/src";
        
        // Output directory for transformed files
        String outputDirectory = "/workspace/jakartaee-mvc-sample/src-fixed";
        
        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(outputDirectory));
        
        // Pattern to match javax.mvc imports or references
        Pattern javaxMvcPattern = Pattern.compile("\\bjavax\\.mvc\\b");
        
        // Recursively process all Java files in the source directory
        Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        // Read the file content
                        String content = new String(Files.readAllBytes(file));
                        
                        // Replace all javax.mvc with jakarta.mvc references
                        String fixedContent = javaxMvcPattern.matcher(content).replaceAll("jakarta.mvc");
                        
                        // Write to output directory
                        String outputFile = outputDirectory + file.toString().substring(sourceDirectory.length());
                        Files.createDirectories(Paths.get(outputFile).getParent());
                        Files.write(Paths.get(outputFile), fixedContent.getBytes());
                        
                        System.out.println("Processed: " + file);
                    } catch (IOException e) {
                        System.err.println("Error processing file " + file + ": " + e.getMessage());
                    }
                });
        
        System.out.println("Transformation completed. Fixed files are in " + outputDirectory);
    }
}