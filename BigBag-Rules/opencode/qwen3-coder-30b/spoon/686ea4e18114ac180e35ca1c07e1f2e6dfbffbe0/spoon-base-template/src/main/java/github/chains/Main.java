package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        // Check if the correct number of arguments are provided
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        try {
            // Process all Java files in the directory recursively
            Path dir = Paths.get(sourceDirectory);
            if (!Files.exists(dir)) {
                System.err.println("Directory does not exist: " + sourceDirectory);
                System.exit(1);
            }
            
            // Find all Java files
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(javaFile));
            
            // Apply the javax to jakarta transformation
            String transformedContent = transformImports(content);
            
            // If content changed, write it back
            if (!content.equals(transformedContent)) {
                Files.write(javaFile, transformedContent.getBytes());
                System.out.println("Updated: " + javaFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
    
    private static String transformImports(String content) {
        // Pattern to match javax.* imports and replace with jakarta.*
        Pattern pattern = Pattern.compile("(import\\s+)(javax\\.[^;]+)(;)");
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        boolean found = false;
        
        while (matcher.find()) {
            found = true;
            String match = matcher.group(0);
            String importPath = matcher.group(2);
            String jakartaPath = importPath.replaceFirst("javax\\.", "jakarta\\.");
            String replacement = matcher.group(1) + jakartaPath + matcher.group(3);
            matcher.appendReplacement(result, replacement);
        }
        
        if (found) {
            matcher.appendTail(result);
            return result.toString();
        } else {
            return content;
        }
    }
}