package github.chains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }

        // Process all Java files in the directory recursively
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            paths.forEach(Main::processJavaFile);
        }
    }

    private static void processJavaFile(Path javaFilePath) {
        try {
            // Read the file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(javaFilePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            String originalContent = content.toString();
            String newContent = originalContent;
            
            // Replace javax.jws with jakarta.jws in imports
            newContent = newContent.replaceAll("(import\\s+)javax\\.jws\\.", "$1jakarta.jws.");
            
            // Replace javax.jws annotations with jakarta.jws annotations
            newContent = newContent.replaceAll("@javax\\.jws\\.", "@jakarta.jws.");
            
            // Check if any changes were made
            if (!newContent.equals(originalContent)) {
                // Write the modified content back to the file
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(javaFilePath.toFile()))) {
                    writer.write(newContent);
                }
                System.out.println("Modified: " + javaFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
        }
    }
}