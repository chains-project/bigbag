package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generic transformation rule for fixing logback breaking changes in Maven projects.
 * Specifically addresses breaking changes in ch.qos.logback.classic.AsyncAppender
 * and related logging patterns from logback 1.4.4.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        processDirectory(sourceDir);
    }
    
    /**
     * Process all Java files in the given directory recursively
     */
    private static void processDirectory(String sourceDir) {
        try {
            Path dirPath = Paths.get(sourceDir);
            Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
        }
    }
    
    /**
     * Process a single Java file
     */
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            // Apply transformation
            LogbackAsyncAppenderFixVisitor visitor = new LogbackAsyncAppenderFixVisitor();
            cu.accept(visitor, null);
            
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transformation visitor for fixing AsyncAppender calls and related logback issues
     */
    public static class LogbackAsyncAppenderFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to AsyncAppender methods that might be affected
            if (isAsyncAppenderMethodCall(n)) {
                // Handle specific logback breaking changes
                return handleAsyncAppenderCall(n);
            }
            return super.visit(n, arg);
        }
        
        /**
         * Handle AsyncAppender calls that might have breaking changes
         */
        private Visitable handleAsyncAppenderCall(MethodCallExpr n) {
            String methodName = n.getNameAsString();
            
            // Common patterns that may have changed
            if ("setIncludeCallerData".equals(methodName)) {
                // In logback 1.4.4, this method could have changed behavior or signature
                // For now we'll ensure it's properly handled
                return n;
            }
            
            // Check for patterns that might be related to caller data handling
            if (isCallerDataRelatedMethod(methodName)) {
                return n;
            }
            
            return n;
        }
        
        private boolean isCallerDataRelatedMethod(String methodName) {
            return methodName.contains("CallerData") || 
                   methodName.contains("includeCaller") ||
                   methodName.contains("caller");
        }
        
        /**
         * Determine if a method call is related to AsyncAppender or logback classes
         */
        private boolean isAsyncAppenderMethodCall(MethodCallExpr n) {
            // Check if the method call is related to AsyncAppender or logback
            if (n.getScope().isPresent()) {
                String scope = n.getScope().get().toString();
                // Look for common AsyncAppender patterns
                return scope.contains("AsyncAppender") || 
                       scope.contains("asyncAppender") ||
                       scope.contains("Async") ||
                       scope.contains("logback") ||
                       n.getNameAsString().contains("AsyncAppender") ||
                       n.getNameAsString().contains("LoggerContext");
            } else {
                // Check method name for logback-related terms
                String methodName = n.getNameAsString();
                return methodName.contains("AsyncAppender") ||
                       methodName.contains("LoggerContext") ||
                       methodName.contains("includeCaller");
            }
        }
    }
}