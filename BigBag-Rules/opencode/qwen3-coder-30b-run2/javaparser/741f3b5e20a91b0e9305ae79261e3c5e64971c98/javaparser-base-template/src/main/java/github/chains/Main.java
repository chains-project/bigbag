package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule to fix Jackson StreamReadException compatibility issues
 * in Maven projects. This fixes breaking changes in Jackson 2.13+ where 
 * com.fasterxml.jackson.core.exc.StreamReadException was moved/removed.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        // Find all Java files in the directory and process them
        List<Path> javaFiles = Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path file : javaFiles) {
            processFile(file);
        }
    }
    
    private static void processFile(Path filePath) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(inputStream);
            
            // Apply transformation to fix StreamReadException references
            cu.accept(new StreamReadExceptionFixVisitor(), null);
            
            // Save modified file
            cu.toString(); // Force regeneration of file content
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}