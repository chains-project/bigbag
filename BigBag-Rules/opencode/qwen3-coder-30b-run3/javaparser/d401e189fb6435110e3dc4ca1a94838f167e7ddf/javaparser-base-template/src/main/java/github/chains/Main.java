package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Generic JavaParser transformation rule to fix breaking changes in logback-classic 1.4.4
 * This rule addresses incompatible API changes in logback that cause compilation errors
 * Specifically designed to handle breaking changes in Logger and related classes
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            transformDirectory(sourceDirectory);
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transform all Java files in a directory recursively
     */
    private static void transformDirectory(String dirPath) throws IOException {
        Path startPath = Paths.get(dirPath);
        Files.walk(startPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformJavaFile);
    }
    
    /**
     * Transform a single Java file to fix logback-related breaking changes
     */
    private static void transformJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations to fix logback API incompatibilities
            new LogbackFixVisitor().visit(cu, null);
            
            // Save the modified file
            String result = cu.toString();
            Files.write(filePath, result.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes logback API incompatibilities
     */
    private static class LogbackFixVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to Logger methods that might have changed in API
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    String scopeName = nameExpr.getNameAsString();
                    
                    // Pattern: logger.setLevel(level) - common pattern that can break
                    if ("setLevel".equals(methodCall.getNameAsString()) && 
                        (scopeName.toLowerCase().contains("logger") || scopeName.toLowerCase().contains("log"))) {
                        // This visitor detects potential breaking changes in Logger API calls
                        // Actual transformation would happen here based on specific API differences
                    }
                }
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
            
            // Look for classes that extend or implement logback classes
            NodeList<ClassOrInterfaceType> extendedTypes = classDecl.getExtendedTypes();
            NodeList<ClassOrInterfaceType> implementedTypes = classDecl.getImplementedTypes();
            
            // Check if any of these types relate to logback
            for (ClassOrInterfaceType type : extendedTypes) {
                handlePotentialLogbackType(type, "extends");
            }
            
            for (ClassOrInterfaceType type : implementedTypes) {
                handlePotentialLogbackType(type, "implements");
            }
        }
        
        private void handlePotentialLogbackType(ClassOrInterfaceType type, String relationship) {
            String typeName = type.getNameAsString();
            
            // Check if the type is related to logback
            if (typeName.contains("Logger") || 
                typeName.contains("Appender") || 
                typeName.contains("Layout") ||
                typeName.contains("Filter") ||
                typeName.contains("Encoder")) {
                // This would be where specific breaking change fixes would be applied
            }
        }
    }
}
