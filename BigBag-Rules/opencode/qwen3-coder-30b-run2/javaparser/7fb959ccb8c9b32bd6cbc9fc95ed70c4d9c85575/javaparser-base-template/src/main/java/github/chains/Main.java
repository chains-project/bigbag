package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing Jetty 11 breaking change from SelectChannelConnector to ServerConnector
 * This transformation replaces all occurrences of SelectChannelConnector with ServerConnector
 * in Java source files to make them compatible with Jetty 11.
 */
public class Main {
    
    public static void main(String[] args) {
        String sourceDir = args.length > 0 ? args[0] : ".";
        
        try {
            // Process all Java files recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation complete! All SelectChannelConnector references have been replaced with ServerConnector.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = Files.readString(filePath);
            boolean modified = false;
            
            // Remove imports of SelectChannelConnector
            String originalContent = content;
            content = content.replaceAll(
                "(import\\s+org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;\\s*)",
                "");
            
            // Replace SelectChannelConnector with ServerConnector in class references
            content = content.replaceAll(
                "\\bSelectChannelConnector\\b", 
                "ServerConnector");
            
            // If changes were made, rewrite the file
            if (!content.equals(originalContent)) {
                Files.writeString(filePath, content);
                System.out.println("Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}