package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jcabi-http/src";
        Path inputDir = Paths.get(sourceDir);
        
        if (!Files.exists(inputDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find all Java files recursively and process them
        Files.walk(inputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the entire file content
            String content = new String(Files.readAllBytes(filePath));
            String originalContent = content;
            
            // Transform hamcrest imports from hamcrest-library to hamcrest-core
            content = content.replaceAll(
                "import org\\.hamcrest\\.library\\.(.*);",
                "import org.hamcrest.$1;"
            );
            
            // Also fix explicit Matchers imports
            content = content.replaceAll(
                "import org\\.hamcrest\\.library\\.Matchers;",
                "import org.hamcrest.Matchers;"
            );
            
            // Write back if changed
            if (!content.equals(originalContent)) {
                Files.write(filePath, content.getBytes());
                System.out.println("Processed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}