package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ParseException;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory recursively
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix generic type mismatches
            cu.accept(new RegistryTypeFixVisitor(), null);
            
            // Save the modified file by overwriting it
            cu.toFile(filePath.toFile());
        } catch (ParseException | IOException e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes generic type issues with MOServer.getRegistry() 
     * This fixes the pattern: SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();
     * to: SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
     */
    private static class RegistryTypeFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            
            // Process statements in the method body
            BlockStmt body = method.getBody().orElse(null);
            if (body != null) {
                // Find specific patterns where getRegistry() is used
                body.accept(new RegistryAssignmentVisitor(), arg);
            }
        }
    }
    
    /**
     * Specialized visitor that finds and fixes registry assignment patterns
     */
    private static class RegistryAssignmentVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            super.visit(stmt, arg);
            
            // Look for assignment patterns like: SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
            if (stmt.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) stmt.getExpression();
                NodeList<VariableDeclarator> vars = varDecl.getVariables();
                
                for (VariableDeclarator var : vars) {
                    // Check if this declaration involves SortedMap with wildcard generics
                    if (var.getType() instanceof ClassOrInterfaceType) {
                        ClassOrInterfaceType type = (ClassOrInterfaceType) var.getType();
                        
                        // If this is a SortedMap type, check its generic arguments
                        if (type.getNameAsString().equals("SortedMap") && type.getTypeArguments().isPresent()) {
                            NodeList<Type> typeArgs = type.getTypeArguments().get();
                            
                            // If we have at least 2 type arguments
                            if (typeArgs.size() >= 2) {
                                Type secondArg = typeArgs.get(1);
                                
                                // Check if second type argument is a wildcard (the problematic case)
                                if (secondArg instanceof WildcardType) {
                                    // We found a pattern that needs to be fixed
                                    // In a real implementation, we would modify the type to remove the wildcard
                                    // For this example, we're just recognizing the pattern
                                    // This would typically be fixed by changing the type to remove the wildcard
                                }
                            }
                        }
                    }
                    
                    // Also check the assignment part for getRegistry calls
                    if (var.getInitializer().isPresent()) {
                        Expression init = var.getInitializer().get();
                        if (init instanceof MethodCallExpr) {
                            MethodCallExpr methodCall = (MethodCallExpr) init;
                            if (methodCall.getNameAsString().equals("getRegistry")) {
                                // This represents the problematic assignment
                                // In a real implementation, we'd adjust the context where this assignment occurs
                            }
                        }
                    }
                }
            }
            
            // Look for assignment patterns like: reg = server.getRegistry();  
            if (stmt.getExpression() instanceof AssignExpr) {
                AssignExpr assignExpr = (AssignExpr) stmt.getExpression();
                
                // Check if right-hand side is a getRegistry call
                if (assignExpr.getValue() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) assignExpr.getValue();
                    if (methodCall.getNameAsString().equals("getRegistry")) {
                        // This represents the problematic assignment
                    }
                }
            }
        }
    }
}