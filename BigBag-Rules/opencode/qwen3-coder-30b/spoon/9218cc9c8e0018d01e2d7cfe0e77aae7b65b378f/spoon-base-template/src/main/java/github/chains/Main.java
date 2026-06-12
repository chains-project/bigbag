package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        String sourceDirectory = "/workspace/wicket-crudifier/src/main/java";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        System.out.println("Applying javax.validation to jakarta.validation transformation to: " + sourceDirectory);
        
        // Process all Java files in the directory
        try {
            processDirectory(Paths.get(sourceDirectory));
            System.out.println("Transformation complete!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    processDirectory(entry);
                } else if (entry.toString().endsWith(".java")) {
                    processFile(entry);
                }
            }
        }
    }
    
    private static void processFile(Path file) throws IOException {
        // Read the file content
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        
        // Replace javax.validation with jakarta.validation in imports and type references
        String newContent = content
            .replaceAll("import javax\\.validation\\.(.*?);", "import jakarta.validation.$1;")
            .replaceAll("javax\\.validation\\.(\\w+)", "jakarta.validation.$1");
        
        // Check if changes were made
        if (!content.equals(newContent)) {
            Files.write(file, newContent.getBytes(StandardCharsets.UTF_8));
            System.out.println("Updated: " + file);
        }
    }
}