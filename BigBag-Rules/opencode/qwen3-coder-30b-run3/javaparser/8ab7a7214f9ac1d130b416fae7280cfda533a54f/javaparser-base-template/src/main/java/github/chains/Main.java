package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processDirectory(String dirPath) throws IOException {
        Path startDir = Paths.get(dirPath);
        Files.walk(startDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformations
            TransformationVisitor visitor = new TransformationVisitor();
            visitor.visit(cu, null);
            
            // Write back changes
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
                writer.print(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Handle method calls to the old API that needs to be updated
            // This is a generic implementation that can be customized for specific cases
            handleBreakingChange(methodCall);
        }
        
        private void handleBreakingChange(MethodCallExpr methodCall) {
            // Generic method to detect and transform breaking changes
            // In a real implementation, this would have specific logic for detected breaking changes
            try {
                // Example logic for handling breaking changes:
                // 1. Check if method call is to a known breaking API
                // 2. Transform the call site to match new API
                
                // For demonstration purposes, just print info about the call
                String className = getClassNameFromMethodCall(methodCall);
                String methodName = methodCall.getNameAsString();
                
                if (className != null && className.contains("acceptance")) {
                    System.out.println("Found potential breaking call: " + className + "." + methodName);
                }
            } catch (Exception e) {
                System.err.println("Error handling breaking change: " + e.getMessage());
            }
        }
        
        private String getClassNameFromMethodCall(MethodCallExpr methodCall) {
            if (methodCall.getScope().isPresent()) {
                com.github.javaparser.ast.expr.Expression scope = methodCall.getScope().get();
                if (scope.isNameExpr()) {
                    return scope.asNameExpr().getNameAsString();
                } else if (scope.isFieldAccessExpr()) {
                    return scope.asFieldAccessExpr().getScope().toString();
                }
            }
            return null;
        }
    }
}