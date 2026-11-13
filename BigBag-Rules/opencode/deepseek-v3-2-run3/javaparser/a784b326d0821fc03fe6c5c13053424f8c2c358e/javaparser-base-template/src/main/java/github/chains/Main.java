package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming validation imports from javax to jakarta in: " + sourceDir);
        
        try {
            int transformedFiles = transformValidationImports(sourceDir);
            System.out.println("Transformation completed. Modified " + transformedFiles + " file(s).");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformValidationImports(String sourceDir) throws IOException {
        int transformedCount = 0;
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedCount++;
            }
        }
        
        return transformedCount;
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Simple string replacement for javax.validation -> jakarta.validation
        // This handles imports and fully qualified names in code
        String originalContent = content;
        content = content.replaceAll("javax\\.validation\\.", "jakarta.validation.");
        
        if (!content.equals(originalContent)) {
            System.out.println("Transformed file: " + filePath);
            Files.writeString(filePath, content);
            return true;
        }
        
        return false;
    }
}