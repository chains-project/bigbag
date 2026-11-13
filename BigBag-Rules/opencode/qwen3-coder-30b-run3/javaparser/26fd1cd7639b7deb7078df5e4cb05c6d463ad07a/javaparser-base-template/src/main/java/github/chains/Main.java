package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDir = args[0];
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(String dirPath) {
        try {
            Files.walk(Paths.get(dirPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            
            // Simplified approach: Replace the exact problematic patterns
            // Remove ScriptResult import
            content = content.replaceAll("import\\s+com\\.gargoylesoftware\\.htmlunit\\.ScriptResult;\\s*", "");
            
            // Replace ScriptResult usage patterns
            content = content.replaceAll(
                "new\\s+ScriptResult\\s*\\(\\s*(\\w+)\\s*\\)\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
                "$1.toString()"
            );
            
            // Replace remaining ScriptResult usage
            content = content.replaceAll(
                "new\\s+ScriptResult\\s*\\(\\s*(\\w+)\\s*\\)\\.getJavaScriptResult\\(\\)",
                "$1"
            );
            
            // Fix exception message formatting
            content = content.replaceAll(
                "throw new java\\.util\\.NoSuchElementException\\(\"Found no trend chart with ID '\\'%s''\" \\+ toolAttribute\\)",
                "throw new java.util.NoSuchElementException(\"Found no trend chart with tool attribute '\" + toolAttribute + \"'\")"
            );
            
            // Save the modified file
            Files.writeString(filePath, content);
            System.out.println("Processed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}