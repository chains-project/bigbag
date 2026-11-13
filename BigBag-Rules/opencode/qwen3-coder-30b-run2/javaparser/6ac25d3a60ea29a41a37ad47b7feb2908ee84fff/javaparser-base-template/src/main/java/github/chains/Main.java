package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/myfaces-tobago";
        
        try {
            // Find all Java files in the source directory
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Processing " + javaFiles.size() + " Java files...");
            
            for (Path file : javaFiles) {
                processFile(file);
            }
            
            System.out.println("Transformation complete!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            
            // Apply our transformation to fix servlet API 6.0 breaking changes
            String transformedContent = transformContent(content);
            
            // Write back the modified content
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(transformedContent);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Simple regex-based transformation that fixes the servlet 5 to 6 breaking changes:
     * The key pattern causing compilation errors is:
     * AsyncContext ac = request.startAsync(); 
     * becomes:
     * request.startAsync();
     * 
     * This handles the change in startAsync() method signature from returning AsyncContext to void
     */
    private static String transformContent(String content) {
        // Pattern to match: AsyncContext ac = request.startAsync();
        // This matches the specific scenario where startAsync() return value is assigned
        Pattern pattern = Pattern.compile(
            "(AsyncContext\\s+\\w+\\s*=\\s*.*?\\.startAsync\\(\\);)", 
            Pattern.MULTILINE | Pattern.DOTALL
        );
        
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String match = matcher.group(1);
            // Replace with just the method call part
            String replacement = match.replaceAll("AsyncContext\\s+\\w+\\s*=\\s*", "");
            // Remove the semicolon at the end to match the new signature
            replacement = replacement.substring(0, replacement.length() - 1) + ";";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}