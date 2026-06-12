package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path directory = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String updatedContent = content;
            
            // Pattern 1: Replace old Flyway() constructor with new pattern
            // Old: Flyway flyway = new Flyway();
            // New: Flyway flyway = new Flyway(new FluentConfiguration());
            updatedContent = updatedContent.replaceAll(
                "Flyway\\s+(\\w+)\\s*=\\s*new\\s+Flyway\\(\\);",
                "Flyway $1 = new Flyway(new org.flywaydb.core.api.configuration.FluentConfiguration());"
            );
            
            // Pattern 2: Remove old setter calls
            // Remove setDataSource calls - these should be handled via FluentConfiguration
            updatedContent = updatedContent.replaceAll(
                "flyway\\.setDataSource\\([^)]*\\);",
                ""
            );
            
            // Remove setClassLoader calls - these should be handled via FluentConfiguration
            updatedContent = updatedContent.replaceAll(
                "flyway\\.setClassLoader\\([^)]*\\);",
                ""
            );
            
            // Remove setLocations calls - these should be handled via FluentConfiguration
            updatedContent = updatedContent.replaceAll(
                "flyway\\.setLocations\\([^)]*\\);",
                ""
            );
            
            // Remove setValidateOnMigrate calls - these should be handled via FluentConfiguration
            updatedContent = updatedContent.replaceAll(
                "flyway\\.setValidateOnMigrate\\([^)]*\\);",
                ""
            );
            
            // If content changed, write it back
            if (!updatedContent.equals(content)) {
                Files.write(filePath, updatedContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}