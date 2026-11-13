package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule to fix jakarta.servlet-api 6.0.0 breaking changes.
 * Specifically addresses the removal of AsyncContext.startAsync(Runnable) method.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);

        // Process all Java files in the source directory
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile.toFile());
                    
                    // Apply transformation to fix AsyncContext.startAsync(Runnable) calls
                    AsyncContextFixer fixer = new AsyncContextFixer();
                    cu.accept(fixer, null);
                    
                    System.out.println("Processed: " + javaFile);
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            }
        }
        System.out.println("Transformation completed.");
    }

    /**
     * Visitor that fixes AsyncContext.startAsync(Runnable) calls by removing the Runnable argument
     */
    private static class AsyncContextFixer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ExpressionStmt expressionStmt, Void arg) {
            // Check if this statement contains a method call to startAsync with exactly one argument
            if (expressionStmt.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expressionStmt.getExpression();
                
                // Only transform if it's a call to startAsync with exactly one parameter
                if ("startAsync".equals(methodCall.getNameAsString()) && 
                    methodCall.getArguments().size() == 1) {
                    
                    // Create a new method call without the Runnable argument
                    // We construct this with the scope and method name only
                    MethodCallExpr newMethodCall = new MethodCallExpr(
                        methodCall.getScope().orElse(null), 
                        "startAsync"
                    );
                    
                    // Replace the entire expression statement
                    return new ExpressionStmt(newMethodCall);
                }
            }
            
            return super.visit(expressionStmt, arg);
        }
    }
}