package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path dirPath = Paths.get(sourceDirectory);
        
        try {
            Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String modifiedContent = processContent(content);
            
            if (!content.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath);
            e.printStackTrace();
        }
    }
    
    private static String processContent(String content) {
        // Pattern to exactly match the problematic line:
        // setLineWidth(border.getLineWidth())
        // Transform to: setLineWidth(Float.valueOf(border.getLineWidth()))
        // This handles the exact pattern we need
        String pattern = "setLineWidth\\(\\s*(\\w+)\\.getLineWidth\\(\\)\\s*\\)";
        String replacement = "setLineWidth(Float.valueOf($1.getLineWidth()))";
        
        return content.replaceAll(pattern, replacement);
    }
}