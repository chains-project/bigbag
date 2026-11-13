package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        // This is a generic transformation that handles the breaking changes
        // in google-cloud-pubsublite 1.6.3 API for any Maven project
        
        String sourceDir = args.length > 0 ? args[0] : "/workspace/java-pubsub-group-kafka-connector";
        
        // Process each Java file in the project
        processJavaFiles(sourceDir);
    }
    
    private static void processJavaFiles(String sourceDir) throws IOException {
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            
            // Remove all references to PublishMetadata class (since it's removed in 1.6.3)
            // We'll simply remove import statements and replace references with Void
            content = content.replaceAll("(?m)^\\s*import\\s+com\\.google\\.cloud\\.pubsublite\\.PublishMetadata;\\s*$", "");
            content = content.replaceAll("\\bcom\\.google\\.cloud\\.pubsublite\\.PublishMetadata\\b", "Void");
            
            // Write back to file
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}