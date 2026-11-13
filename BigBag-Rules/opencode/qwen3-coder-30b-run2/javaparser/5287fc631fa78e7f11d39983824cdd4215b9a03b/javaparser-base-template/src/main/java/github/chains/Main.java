package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic JavaParser transformation to fix Jetty 11 breaking changes
 * Specifically addresses removal of SelectChannelConnector and related nio classes
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jadler";
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find all Java files
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            
            // Simple text-based replacement for now
            boolean modified = false;
            String newContent = content;
            
            // Remove the problematic import
            if (content.contains("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                System.out.println("  Removing deprecated import line");
                newContent = content.replaceAll("//.*\\s*org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector\\s*\\r?\\n", "");
                newContent = newContent.replaceAll("import\\s+org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;\\s*\\r?\\n", "");
                modified = true;
            }
            
            // Also check for other nio imports
            if (content.contains("org.eclipse.jetty.server.nio")) {
                System.out.println("  Removing deprecated nio import");
                newContent = newContent.replaceAll("import\\s+org\\.eclipse\\.jetty\\.server\\.nio\\..*;\\s*\\r?\\n", "");
                modified = true;
            }
            
            // Save the modified file if changes were made
            if (modified) {
                Files.write(filePath, newContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Processed: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}