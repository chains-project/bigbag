package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            String content = new String(Files.readAllBytes(javaFilePath));
            String modifiedContent = content;
            
            // Fix imports from javax.validation to jakarta.validation
            modifiedContent = modifiedContent.replaceAll(
                "import javax\\.validation\\.(.*?);", 
                "import jakarta.validation.$1;"
            );
            
            // Fix imports from javax.validation.constraints to jakarta.validation.constraints
            modifiedContent = modifiedContent.replaceAll(
                "import javax\\.validation\\.constraints\\.(.*?);", 
                "import jakarta.validation.constraints.$1;"
            );
            
            // Fix imports from javax.validation.metadata to jakarta.validation.metadata
            modifiedContent = modifiedContent.replaceAll(
                "import javax\\.validation\\.metadata\\.(.*?);", 
                "import jakarta.validation.metadata.$1;"
            );
            
            // If content was modified, write it back
            if (!modifiedContent.equals(content)) {
                Files.write(javaFilePath, modifiedContent.getBytes());
                System.out.println("Fixed imports in: " + javaFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
}