package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {
        // Get the source directory from command line arguments
        if (args.length == 0) {
            System.err.println("Please provide the source directory as an argument");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            processDirectory(sourcePath);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            
            // Apply transformation rules
            String updatedContent = content;
            
            // Rule 1: Replace import statements
            updatedContent = updatedContent.replaceAll(
                "import\\s+org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;",
                "import org.eclipse.jetty.server.ServerConnector;"
            );
            
            // Rule 2: Replace constructor calls
            // Look for new SelectChannelConnector() pattern
            updatedContent = updatedContent.replaceAll(
                "(new\\s+)SelectChannelConnector\\(\\)",
                "$1ServerConnector(server)"
            );
            
            // Rule 3: Handle cases where the constructor is called without parentheses
            updatedContent = updatedContent.replaceAll(
                "SelectChannelConnector\\s*\\(\\s*\\)", 
                "ServerConnector(server)"
            );
            
            // Write back to file if different
            if (!updatedContent.equals(content)) {
                Files.write(filePath, updatedContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}