package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for fixing Kotlin version incompatibility issues
 * when dependencies are compiled with newer Kotlin versions.
 * 
 * This transformation:
 * - Finds Maven POM files with Kotlin compiler plugin configurations
 * - Updates Kotlin version to match dependency metadata requirements
 * - Can be parameterized for different target Kotlin versions
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String TARGET_KOTLIN_VERSION = "1.8.0";
    private static final String KOTLIN_VERSION_PROPERTY = "kotlin.version";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project");
            return;
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        try {
            List<Path> pomFiles = findPomFiles(sourcePath);
            System.out.println("Found " + pomFiles.size() + " POM files");
            
            int updatedFiles = 0;
            for (Path pomFile : pomFiles) {
                if (updateKotlinVersion(pomFile)) {
                    updatedFiles++;
                    System.out.println("Updated: " + pomFile);
                }
            }
            
            System.out.println("Successfully updated " + updatedFiles + " POM files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Recursively finds all pom.xml files in the directory tree
     */
    private static List<Path> findPomFiles(Path startDir) throws IOException {
        List<Path> pomFiles = new ArrayList<>();
        
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("pom.xml")) {
                    pomFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return pomFiles;
    }
    
    /**
     * Updates Kotlin version in a POM file
     * Returns true if the file was modified
     */
    private static boolean updateKotlinVersion(Path pomFile) throws IOException {
        String content = Files.readString(pomFile);
        
        // Parse the POM file as XML (simplified approach for this example)
        // In a real implementation, you would use a proper XML parser
        
        boolean modified = false;
        
        // Pattern 1: Update kotlin.version property
        String propertyPattern = "<" + KOTLIN_VERSION_PROPERTY + ">[^<]+</" + KOTLIN_VERSION_PROPERTY + ">";
        String replacement = "<" + KOTLIN_VERSION_PROPERTY + ">" + TARGET_KOTLIN_VERSION + "</" + KOTLIN_VERSION_PROPERTY + ">";
        
        if (content.matches(".*" + propertyPattern + ".*")) {
            content = content.replaceAll(propertyPattern, replacement);
            modified = true;
        }
        
        // Pattern 2: Update Kotlin Maven plugin version
        String pluginPattern = "<artifactId>kotlin-maven-plugin</artifactId>\\s*<version>[^<]+</version>";
        String pluginReplacement = "<artifactId>kotlin-maven-plugin</artifactId>\\n                    <version>" + TARGET_KOTLIN_VERSION + "</version>";
        
        if (content.matches(".*" + pluginPattern + ".*")) {
            content = content.replaceAll(pluginPattern, pluginReplacement);
            modified = true;
        }
        
        // Pattern 3: Update Kotlin stdlib dependencies
        String[] kotlinArtifacts = {
            "kotlin-stdlib",
            "kotlin-stdlib-jdk7", 
            "kotlin-stdlib-jdk8",
            "kotlin-reflect",
            "kotlin-test",
            "kotlin-test-junit"
        };
        
        for (String artifact : kotlinArtifacts) {
            String artifactPattern = "<artifactId>" + artifact + "</artifactId>\\s*<version>[^<]+</version>";
            String artifactReplacement = "<artifactId>" + artifact + "</artifactId>\\n            <version>" + TARGET_KOTLIN_VERSION + "</version>";
            
            if (content.matches(".*" + artifactPattern + ".*")) {
                content = content.replaceAll(artifactPattern, artifactReplacement);
                modified = true;
            }
        }
        
        if (modified) {
            Files.writeString(pomFile, content);
        }
        
        return modified;
    }
}