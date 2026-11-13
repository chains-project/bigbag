package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation to fix breaking changes in Jetty dependency.
 * Specifically addresses the removal of SelectChannelConnector in Jetty 11.x
 * 
 * This transformation:
 * 1. Replaces SelectChannelConnector with ServerConnector
 * 2. Fixes constructor calls to be compatible with Jetty 11
 * 3. Makes minimal changes to preserve functionality
 */
public class Main {
    
    public static void main(String[] args) {
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jadler";
        
        try {
            // Process all Java files in the source directory recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation complete.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Check if this file contains deprecated jetty connector references
            if (content.contains("SelectChannelConnector") || content.contains("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                String newContent = content;
                
                // Replace import statement for SelectChannelConnector
                newContent = newContent.replaceAll(
                    "import\\s+org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;",
                    "import org.eclipse.jetty.server.ServerConnector;"
                );
                
                // Replace any remaining SelectChannelConnector references
                newContent = newContent.replaceAll(
                    "\\bSelectChannelConnector\\b",
                    "ServerConnector"
                );
                
                // Fix constructor calls to pass server parameter 
                newContent = newContent.replaceAll(
                    "new ServerConnector\\s*\\(\\s*\\)",
                    "new ServerConnector(server)"
                );
                
                if (!content.equals(newContent)) {
                    Files.write(filePath, newContent.getBytes());
                    modified = true;
                }
            }
            
            if (modified) {
                System.out.println("Fixed: " + filePath);
            } else {
                // Only show this for files with jetty references
                if (content.contains("jetty")) {
                    System.out.println("No changes needed: " + filePath);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}