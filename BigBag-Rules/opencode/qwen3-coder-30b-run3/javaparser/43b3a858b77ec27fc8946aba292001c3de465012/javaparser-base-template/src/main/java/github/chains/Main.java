/*
 * Final Generic Transformation Rule for Breaking Changes in slf4j APIs
 * 
 * Problem: In logback 1.4.5+, the Logger class no longer implements 
 * org.slf4j.spi.LoggingEventAware interface, causing compilation errors.
 * 
 * Solution: Remove the casting of LoggerFactory.getLogger(...) to Logger
 * 
 * Pattern to match:
 * ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.TRACE)
 * 
 * Should become:
 * LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.TRACE)
 * 
 * This generic rule removes the problematic cast expression while preserving
 * the rest of the functionality.
 */

package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * Generic transformation to fix breaking changes in slf4j APIs
 * Specifically addresses the removal of org.slf4j.spi.LoggingEventAware
 * in logback 1.4.5+
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the provided directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/pdb/src";
        processDirectory(sourceDirectory);
        System.out.println("Transformation completed.");
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Invalid directory path: " + directoryPath);
            return;
        }
        
        Files.walk(Paths.get(directoryPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> processFile(path.toString()));
    }
    
    private static void processFile(String filePath) {
        try {
            File file = new File(filePath);
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply transformations to fix logging event aware usage
            new LoggingEventAwareFixer().visit(cu, null);
            
            // Save the modified file
            String output = cu.toString();
            Files.write(Paths.get(filePath), output.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds and fixes usages of org.slf4j.spi.LoggingEventAware
     */
    private static class LoggingEventAwareFixer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Look for method calls that have a cast expression as a scope
            if (n.getScope().isPresent() && n.getScope().get() instanceof CastExpr) {
                CastExpr castExpr = (CastExpr) n.getScope().get();
                
                // Check if this is a cast to Logger or related classes that cause the problem
                String castTypeName = castExpr.getType().toString();
                if (castTypeName.contains("Logger") || castTypeName.contains("LoggingEventAware")) {
                    // Try to extract the original expression (MethodCallExpr) that was cast
                    Expression originalExpression = castExpr.getExpression();
                    
                    // Replace the entire expression with the casted expression
                    // This essentially removes the cast that causes the compilation error
                    n.setScope(originalExpression);
                }
            }
        }
    }
}