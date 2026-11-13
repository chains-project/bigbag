package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        processDirectory(sourceDir);
    }

    private static void processDirectory(String dirPath) throws IOException {
        Files.walk(Paths.get(dirPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            
            boolean changed = false;
            
            // Replace SelectChannelConnector with ServerConnector in constructor calls
            String updatedContent = content.replaceAll(
                "(new\\s+)SelectChannelConnector(\\s*\\(.*?\\))",
                "$1ServerConnector$2"
            );
            
            // Handle the case where there's no parentheses (e.g. "new SelectChannelConnector")
            updatedContent = updatedContent.replaceAll(
                "(new\\s+)SelectChannelConnector(?!\\w)",
                "$1ServerConnector"
            );
            
            // Replace imports if needed
            updatedContent = updatedContent.replaceAll(
                "import org\\.eclipse\\.jetty\\.server\\.nio\\.SelectChannelConnector;",
                "import org.eclipse.jetty.server.ServerConnector;"
            );
            
            if (!content.equals(updatedContent)) {
                Files.write(filePath, updatedContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}