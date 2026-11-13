package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the source directory
            String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter";
            processDirectory(new File(sourceDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        Files.walk(directory.toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processJavaFile(path.toFile());
                } catch (Exception e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        String modifiedContent = fixStringContains(content);
        if (!content.equals(modifiedContent)) {
            Files.write(file.toPath(), modifiedContent.getBytes());
            System.out.println("Fixed file: " + file.getAbsolutePath());
        }
    }
    
    private static String fixStringContains(String content) {
        // Pattern to match StringContains constructor calls with boolean parameter
        // This matches: new StringContains(true, "substring") or new StringContains(false, "substring")
        // Handles both cases with and without whitespace
        Pattern stringContainsPattern = Pattern.compile(
            "new\\s+StringContains\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)");
        
        // Pattern to match StringStartsWith constructor calls with boolean parameter
        Pattern stringStartsWithPattern = Pattern.compile(
            "new\\s+StringStartsWith\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)");
        
        // Fix StringContains calls - replace with just the string argument
        String result = stringContainsPattern.matcher(content).replaceAll("new StringContains($2)");
        
        // Fix StringStartsWith calls
        result = stringStartsWithPattern.matcher(result).replaceAll("new StringStartsWith($2)");
        
        return result;
    }
}
    }
    
    private static void processDirectory(File directory) throws IOException {
        Files.walk(directory.toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processJavaFile(path.toFile());
                } catch (Exception e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        String modifiedContent = fixStringContains(content);
        if (!content.equals(modifiedContent)) {
            Files.write(file.toPath(), modifiedContent.getBytes());
            System.out.println("Fixed file: " + file.getAbsolutePath());
        }
    }
    
    private static String fixStringContains(String content) {
        // Pattern to match StringContains constructor calls with boolean parameter
        // This matches: new StringContains(true, "substring") or new StringContains(false, "substring")
        // This is more precise - matches exactly the pattern we want to fix
        Pattern stringContainsPattern = Pattern.compile(
            "new\\s+StringContains\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)");
        
        // Pattern to match StringStartsWith constructor calls with boolean parameter
        Pattern stringStartsWithPattern = Pattern.compile(
            "new\\s+StringStartsWith\\s*\\(\\s*(true|false)\\s*,\\s*(\"[^\"]*\")\\s*\\)");
        
        // Fix StringContains calls - replace with just the string argument
        String result = stringContainsPattern.matcher(content).replaceAll("new StringContains($2)");
        
        // Fix StringStartsWith calls
        result = stringStartsWithPattern.matcher(result).replaceAll("new StringStartsWith($2)");
        
        return result;
    }
}
