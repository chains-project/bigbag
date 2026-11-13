package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        // Check if the source directory is provided
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all POM files in the directory
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith("pom.xml"))) {
            
            List<Path> pomFiles = paths.collect(Collectors.toList());
            
            for (Path pomFile : pomFiles) {
                processPomFile(pomFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processPomFile(Path pomFile) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
            
            // Apply transformation to fix Kotlin version compatibility issues
            String newContent = fixKotlinVersionCompatibility(content);
            
            // Write back to file if it has changed
            if (!newContent.equals(content)) {
                Files.write(pomFile, newContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Fixed Kotlin compatibility in: " + pomFile);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + pomFile + ": " + e.getMessage());
        }
    }
    
    /**
     * Fix Kotlin version compatibility issues by updating the Kotlin version in pom.xml
     * This is a generic transformation that can be applied to Maven projects with Kotlin version conflicts
     */
    private static String fixKotlinVersionCompatibility(String pomContent) {
        // Replace Kotlin version from 1.6.0 to 1.8.0 to match okio 3.4.0 requirements
        // This is a generic rule that can be adapted for other dependency version issues
        String updatedContent = pomContent.replace("<kotlin.version>1.6.0</kotlin.version>", 
                                                   "<kotlin.version>1.8.0</kotlin.version>");
        
        // Also update the kotlin-maven-plugin version to match
        updatedContent = updatedContent.replace("<version>${kotlin.version}</version>", 
                                                "<version>1.8.0</version>");
        
        // If the project has Kotlin dependencies, update them as well
        updatedContent = updatedContent.replace("<artifactId>kotlin-test-junit</artifactId>\n      <version>${kotlin.version}</version>", 
                                                "<artifactId>kotlin-test-junit</artifactId>\n      <version>1.8.0</version>");
        
        updatedContent = updatedContent.replace("<artifactId>kotlin-stdlib</artifactId>\n        <version>${kotlin.version}</version>", 
                                                "<artifactId>kotlin-stdlib</artifactId>\n        <version>1.8.0</version>");
        
        return updatedContent;
    }
}