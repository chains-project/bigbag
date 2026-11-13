package github.chains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation to fix Hamcrest 2.2 breaking changes.
 * Specifically addresses the removal of org.hamcrest.Matchers class.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the given directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jcabi-http";
        
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find and process all Java files
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Read the file content
            StringBuilder content = new StringBuilder();
            String line;
            
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                while ((line = reader.readLine()) != null) {
                    // Replace the problematic import
                    if (line.contains("import org.hamcrest.Matchers;")) {
                        line = line.replace("import org.hamcrest.Matchers;", 
                                          "import org.hamcrest.CoreMatchers;");
                    }
                    // Replace usages of Matchers.method() with CoreMatchers.method()
                    else if (line.contains("Matchers.")) {
                        // Handle both simple calls like Matchers.containsString(...)
                        // and generic calls like Matchers.<URI>hasProperty(...)
                        line = line.replaceAll("Matchers\\.(<[^>]*>)?([a-zA-Z0-9_]+)\\(", "CoreMatchers.$2(");
                    }
                    content.append(line).append("\n");
                }
            }
            
            // Write back the modified content
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                writer.write(content.toString());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
