package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {
    
    // The breaking change pattern: replacing SessionImplementor with SharedSessionContractImplementor
    // in nullSafeGet and nullSafeSet method parameter types
    private static final Map<String, String> REPLACEMENTS = Map.of(
        "SessionImplementor sessionImplementor", 
        "SharedSessionContractImplementor sessionImplementor",
        
        "SessionImplementor session", 
        "SharedSessionContractImplementor session",
        
        "org.hibernate.engine.spi.SessionImplementor", 
        "org.hibernate.engine.spi.SharedSessionContractImplementor"
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hibernate UserType API migration to: " + sourceDir);
        
        // Process all Java files in the directory
        processDirectory(new File(sourceDir));
        
        System.out.println("Transformation complete.");
    }
    
    private static void processDirectory(File dir) throws IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Directory not found: " + dir.getPath());
            return;
        }
        
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file);
            }
        }
    }
    
    private static void processJavaFile(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> modifiedLines = new ArrayList<>();
        boolean fileModified = false;
        boolean isUserTypeFile = false;
        
        // Check if this is a UserType implementation
        for (String line : lines) {
            if (line.contains("implements") && line.contains("UserType")) {
                isUserTypeFile = true;
                break;
            }
            if (line.contains("nullSafeGet") || line.contains("nullSafeSet")) {
                isUserTypeFile = true;
                break;
            }
        }
        
        if (!isUserTypeFile) {
            return; // Skip files that aren't UserType implementations
        }
        
        System.out.println("Processing UserType file: " + file.getPath());
        
        for (String line : lines) {
            String modifiedLine = line;
            
            // Apply replacements for this specific breaking change
            for (Map.Entry<String, String> replacement : REPLACEMENTS.entrySet()) {
                if (line.contains(replacement.getKey())) {
                    modifiedLine = modifiedLine.replace(replacement.getKey(), replacement.getValue());
                    if (!modifiedLine.equals(line)) {
                        fileModified = true;
                        System.out.println("  Applied replacement in line: " + line.trim());
                    }
                }
            }
            
            // Also handle the specific case for method signatures
            if (line.contains("nullSafeGet") || line.contains("nullSafeSet")) {
                String tempLine = modifiedLine;
                for (Map.Entry<String, String> replacement : REPLACEMENTS.entrySet()) {
                    tempLine = tempLine.replace(replacement.getKey(), replacement.getValue());
                }
                if (!tempLine.equals(modifiedLine)) {
                    modifiedLine = tempLine;
                    fileModified = true;
                    System.out.println("  Updated method signature: " + line.trim());
                }
            }
            
            modifiedLines.add(modifiedLine);
        }
        
        if (fileModified) {
            // Write the modified file back
            Files.write(file.toPath(), modifiedLines, StandardCharsets.UTF_8);
            System.out.println("  Successfully updated: " + file.getName());
        }
    }
}