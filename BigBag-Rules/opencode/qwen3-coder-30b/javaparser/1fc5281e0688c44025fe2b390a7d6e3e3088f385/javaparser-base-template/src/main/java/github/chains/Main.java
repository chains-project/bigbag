package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.out.println("Source directory does not exist: " + sourceDirectory);
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
                "import org.eclipse.jetty.server.nio.SelectChannelConnector;",
                "import org.eclipse.jetty.server.ServerConnector;"
            );
            if (!newContent.equals(content)) {
                modified = true;
            }
            content = newContent;
            
            // Replace constructor calls - this handles the main issue
            // Replace: new SelectChannelConnector()
            // With: new ServerConnector(server)
            newContent = content.replaceAll(
                "new SelectChannelConnector\\(\\)",
                "new ServerConnector(server)"
            );
            if (!newContent.equals(content)) {
                modified = true;
            }
            content = newContent;
            
            // Replace: new SelectChannelConnector(port)
            // With: new ServerConnector(server, port)
            newContent = content.replaceAll(
                "new SelectChannelConnector\\(([^)]+)\\)",
                "new ServerConnector(server, $1)"
            );
            if (!newContent.equals(content)) {
                modified = true;
            }
            content = newContent;
            
            // If we made changes, write back to file
            if (modified) {
                Files.write(filePath, content.getBytes());
                System.out.println("Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}