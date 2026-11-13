package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.Pattern;

/**
 * Generic transformation for fixing breaking changes in Jetty dependencies.
 * Specifically fixes the removal of SelectChannelConnector in Jetty 11.x
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jadler";
        
        // Process all Java files in the source directory
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path path) {
        try {
            String content = new String(Files.readAllBytes(path));
            boolean modified = false;
            
            // Replace import statement
            content = content.replaceAll(
                "import org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;",
                "import org.eclipse.jetty.server.ServerConnector;"
            );
            
            // Replace constructor call
            content = content.replaceAll(
                "new SelectChannelConnector\\(\\)",
                "new ServerConnector(server)"
            );
            
            // If changes were made, write back to file
            if (!content.equals(new String(Files.readAllBytes(path)))) {
                Files.write(path, content.getBytes());
                System.out.println("Modified file: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
