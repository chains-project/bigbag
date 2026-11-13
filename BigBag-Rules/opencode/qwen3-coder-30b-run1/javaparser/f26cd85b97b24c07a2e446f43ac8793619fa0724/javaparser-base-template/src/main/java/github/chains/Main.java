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
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Replace import statement
            String newContent = content.replaceAll(
                "import org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;",
                "import org.eclipse.jetty.server.ServerConnector;"
            );
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace constructor calls with no arguments
            newContent = content.replaceAll(
                "new SelectChannelConnector\\(\\s*\\)",
                "new ServerConnector(server)"
            );
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace constructor calls with port argument
            newContent = content.replaceAll(
                "new SelectChannelConnector\\(\\s*(\\w+)\\s*\\)",
                "new ServerConnector(server, $1)"
            );
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace constructor calls with other arguments
            newContent = content.replaceAll(
                "new SelectChannelConnector\\(\\s*([^)]+?)\\s*\\)",
                "new ServerConnector(server, $1)"
            );
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            if (modified) {
                Files.write(filePath, content.getBytes());
                System.out.println("Modified: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}