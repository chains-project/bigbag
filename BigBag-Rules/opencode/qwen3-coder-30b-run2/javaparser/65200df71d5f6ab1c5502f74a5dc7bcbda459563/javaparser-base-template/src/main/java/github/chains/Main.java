package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the project
            Path projectPath = Paths.get(args.length > 0 ? args[0] : "/workspace/sorald");
            
            // Walk through the directory recursively
            Files.walk(projectPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            
            // Replace the specific problematic method call
            String newContent = content.replace("addEnabledLanguages", "setEnabledLanguages");
            
            if (!content.equals(newContent)) {
                Files.write(filePath, newContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}