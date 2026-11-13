package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic file transformation rule for migrating from javax.validation to jakarta.validation
 * due to Hibernate Validator 8.0.0.Final breaking change.
 * 
 * Breaking Change Pattern:
 * - Old API: javax.validation.*
 * - New API: jakarta.validation.*
 * - Transformation: Replace all occurrences of "javax.validation" with "jakarta.validation"
 * 
 * This transformation is applicable to ANY Maven project affected by the same breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation migrates javax.validation imports to jakarta.validation");
            System.err.println("in ALL Java files within the specified directory and its subdirectories.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: javax.validation -> jakarta.validation");
        
        try {
            List<Path> modifiedFiles = new ArrayList<>();
            int totalReplacements = 0;
            
            // Walk through all Java files
            try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
                List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
                
                for (Path file : javaFiles) {
                    String content = Files.readString(file);
                    
                    // Check if file contains javax.validation
                    if (content.contains("javax.validation")) {
                        // Replace javax.validation with jakarta.validation
                        String newContent = content.replace("javax.validation", "jakarta.validation");
                        
                        // Count replacements
                        int count = countReplacements(content);
                        if (count > 0) {
                            // Write updated content
                            Files.writeString(file, newContent);
                            modifiedFiles.add(file);
                            
                            System.out.println("  Updated " + file.getFileName() + ": " + count + " replacements");
                            totalReplacements += count;
                        }
                    }
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Files modified: " + modifiedFiles.size());
            System.out.println("  Total replacements: " + totalReplacements);
            
            if (totalReplacements > 0) {
                System.out.println("\nTransformation complete. Code has been updated.");
                System.out.println("\nThis generic transformation rule can be applied to any project");
                System.out.println("affected by the javax.validation -> jakarta.validation migration.");
                System.out.println("\nTo apply to another project, simply run:");
                System.out.println("  java Main /path/to/other/project/src/main/java");
            } else {
                System.out.println("\nNo javax.validation references found.");
            }
            
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int countReplacements(String content) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf("javax.validation", index)) != -1) {
            count++;
            index += "javax.validation".length();
        }
        return count;
    }
}