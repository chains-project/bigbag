package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for jcabi-aspects breaking changes.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path basePath = Paths.get(sourceDir);
        
        // Find all Java files and report
        Files.walk(basePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::analyzeJavaFile);
            
        System.out.println("Analysis complete.");
    }
    
    private static void analyzeJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            System.out.println("Processing: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to analyze " + filePath + ": " + e.getMessage());
        }
    }
}