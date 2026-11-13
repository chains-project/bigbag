package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
        System.out.println("Transformation complete!");
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Read the Java file
            String content = Files.readString(javaFilePath);
            
            // Replace javax.mvc with jakarta.mvc in imports
            String modifiedContent = content
                .replaceAll("import javax\\.mvc\\.", "import jakarta.mvc.")
                .replaceAll("javax\\.mvc\\.", "jakarta.mvc.");
            
            // Write back the modified content
            Files.writeString(javaFilePath, modifiedContent);
            
            System.out.println("Processed: " + javaFilePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
        }
    }
}