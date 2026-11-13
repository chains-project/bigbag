package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Remove import statements for PublishMetadata
            String newContent = content.replaceAll(
                "import\\s+com\\.google\\.cloud\\.pubsublite\\.PublishMetadata;\\s*", "");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace Publisher<PublishMetadata> with Publisher (no type arguments)
            newContent = content.replaceAll(
                "Publisher\\s*<\\s*PublishMetadata\\s*>", "Publisher");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // If file was modified, write back to disk
            if (modified) {
                Files.write(filePath, content.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Remove import statements for PublishMetadata
            String newContent = content.replaceAll(
                "import\\s+com\\.google\\.cloud\\.pubsublite\\.PublishMetadata;\\s*", "");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace Publisher<PublishMetadata> with Publisher (no type arguments) - most common case
            newContent = content.replaceAll(
                "Publisher\\s*<\\s*PublishMetadata\\s*>", "Publisher");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Replace Publisher<PublishMetadata> with Publisher<> (empty type arguments)
            newContent = content.replaceAll(
                "Publisher\\s*<\\s*PublishMetadata\\s*>", "Publisher<>");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // Handle Publisher<PublishMetadata> with whitespace variations
            newContent = content.replaceAll(
                "Publisher\\s*<\\s*PublishMetadata\\s*>", "Publisher");
            if (!newContent.equals(content)) {
                modified = true;
                content = newContent;
            }
            
            // If file was modified, write back to disk
            if (modified) {
                Files.write(filePath, content.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
