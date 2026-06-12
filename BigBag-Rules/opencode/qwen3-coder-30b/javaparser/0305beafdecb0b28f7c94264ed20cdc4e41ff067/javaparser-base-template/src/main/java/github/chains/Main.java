package github.chains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Simple text-based transformation to fix MySQL JDBC driver breaking change
 * from com.mysql.jdbc.exceptions to com.mysql.cj.jdbc.exceptions
 */
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

        // Walk through all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            // Apply the transformation
            String transformedContent = content.toString()
                .replaceAll("com\\.mysql\\.jdbc\\.exceptions\\.", "com.mysql.cj.jdbc.exceptions.");
            
            // Write back to the file if it was changed
            if (!content.toString().equals(transformedContent)) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                    writer.write(transformedContent);
                }
                System.out.println("Fixed: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}