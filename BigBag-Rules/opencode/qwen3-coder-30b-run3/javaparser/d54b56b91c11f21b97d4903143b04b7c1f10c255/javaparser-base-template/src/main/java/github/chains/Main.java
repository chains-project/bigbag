package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            // Find all Java files in the directory
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Visit all nodes and make transformations
            new PubSubLiteFixerVisitor().visit(cu, null);
            
            // Save the modified file
            FileWriter writer = new FileWriter(filePath.toFile());
            writer.write(new DefaultPrettyPrinter().print(cu));
            writer.close();
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that fixes the breaking changes in Google Cloud Pub/Sub Lite API:
     * - Removes references to PublishMetadata class
     * - Removes calls to setContext() method
     */
    private static class PubSubLiteFixerVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Remove import of PublishMetadata
            if (importDecl.getNameAsString().equals("com.google.cloud.pubsublite.PublishMetadata")) {
                importDecl.remove();
            }
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
        }
        
        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            super.visit(stmt, arg);
            
            // Look for expressions that contain setContext calls
            Expression expr = stmt.getExpression();
            
            // Check if expression is a method call containing setContext
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                
                // Check if it's a setContext call
                if (methodCall.getNameAsString().equals("setContext")) {
                    // Remove the entire statement for setContext
                    stmt.remove();
                    return;
                }
                
                // Also handle chained builders that contain setContext
                if (containsSetContext(methodCall)) {
                    stmt.remove();
                    return;
                }
            }
        }
        
        private boolean containsSetContext(MethodCallExpr methodCall) {
            // Recursively check if the method call or any of its arguments 
            // or chained calls contain setContext
            if (methodCall.getNameAsString().equals("setContext")) {
                return true;
            }
            
            // Check arguments for setContext calls
            NodeList<Expression> arguments = methodCall.getArguments();
            List<Expression> args = new ArrayList<>();
            for (Expression arg : arguments) {
                args.add(arg);
            }
            for (Expression arg : args) {
                if (arg instanceof MethodCallExpr) {
                    if (containsSetContext((MethodCallExpr) arg)) {
                        return true;
                    }
                }
            }
            
            // Check the scope for chained method calls
            Optional<Expression> scope = methodCall.getScope();
            if (scope.isPresent() && scope.get() instanceof MethodCallExpr) {
                return containsSetContext((MethodCallExpr) scope.get());
            }
            
            return false;
        }
    }
}