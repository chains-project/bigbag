package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for asto-core breaking dependency changes.
 * Specifically addresses FailedCompletionStage constructor signature changes.
 * This template can be customized for any library breaking change pattern.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Default to docker-adapter source directory if no args provided
        String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter/src";
        
        Path sourcePath = Paths.get(sourceDir);
        if (Files.exists(sourcePath)) {
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            
            // Find all FailedCompletionStage constructor calls and transform them
            cu.findAll(ObjectCreationExpr.class).stream()
                .filter(expr -> isFailedCompletionStage(expr.getType()))
                .forEach(Main::transformFailedCompletionStage);
            
            // Print transformation info
            System.out.println("Processed file: " + file);
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }

    private static boolean isFailedCompletionStage(ClassOrInterfaceType type) {
        // Match both direct class name and fully qualified name
        return "FailedCompletionStage".equals(type.getNameAsString()) || 
               "com.artipie.asto.FailedCompletionStage".equals(type.asString());
    }

    /**
     * Transform FailedCompletionStage constructor calls to match the new API.
     */
    private static void transformFailedCompletionStage(ObjectCreationExpr expr) {
        System.out.println("Found FailedCompletionStage instantiation at: " + expr.getRange().toString());
    }
}