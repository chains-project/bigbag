package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the given directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/code-coverage-api-plugin";
        
        try {
            processDirectory(sourceDirectory);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
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
            String code = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(code);
            
            // Apply transformations
            boolean modified = applyTransformations(cu);
            
            if (modified) {
                // Save the modified file
                try (PrintWriter out = new PrintWriter(new FileWriter(filePath.toString()))) {
                    out.println(LexicalPreservingPrinter.print(cu));
                }
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean applyTransformations(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method declarations
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            modified |= transformMethod(method);
        }
        
        return modified;
    }
    
    private static boolean transformMethod(MethodDeclaration method) {
        boolean modified = false;
        
        // Look for all MethodCallExpr in the method body
        for (Statement stmt : method.getBody().orElseThrow().getStatements()) {
            // Check if this is an ExpressionStmt
            if (stmt instanceof ExpressionStmt) {
                ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                Expression expr = exprStmt.getExpression();
                
                // Case 1: Direct chained call like: new ScriptResult(result).getJavaScriptResult()
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) expr;
                    
                    // Check if the method call has a scope which is an ObjectCreationExpr
                    if (methodCall.getScope().isPresent()) {
                        Expression scope = methodCall.getScope().get();
                        
                        if (scope instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                            
                            if (objectCreation.getType().isClassOrInterfaceType()) {
                                ClassOrInterfaceType type = objectCreation.getType().asClassOrInterfaceType();
                                
                                // If this is a ScriptResult constructor call followed by getJavaScriptResult()
                                if ("ScriptResult".equals(type.getNameAsString()) && 
                                    "getJavaScriptResult".equals(methodCall.getNameAsString())) {
                                    
                                    // Replace the entire expression with just the argument to ScriptResult constructor
                                    if (objectCreation.getArguments().size() > 0) {
                                        Expression resultExpr = objectCreation.getArguments().get(0);
                                        exprStmt.setExpression(resultExpr);
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Case 2: Combined assignment like: Object scriptResult = new ScriptResult(result).getJavaScriptResult();
                if (expr instanceof VariableDeclarationExpr) {
                    VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                    for (VariableDeclarator var : varDecl.getVariables()) {
                        if (var.getInitializer().isPresent()) {
                            Expression initializer = var.getInitializer().get();
                            if (initializer instanceof MethodCallExpr) {
                                MethodCallExpr methodCall = (MethodCallExpr) initializer;
                                
                                if (methodCall.getScope().isPresent()) {
                                    Expression scope = methodCall.getScope().get();
                                    if (scope instanceof ObjectCreationExpr) {
                                        ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                                        
                                        if (objectCreation.getType().isClassOrInterfaceType()) {
                                            ClassOrInterfaceType type = objectCreation.getType().asClassOrInterfaceType();
                                            
                                            // If this is a ScriptResult constructor call followed by getJavaScriptResult()
                                            if ("ScriptResult".equals(type.getNameAsString()) && 
                                                "getJavaScriptResult".equals(methodCall.getNameAsString())) {
                                                
                                                // Replace with just the argument to ScriptResult constructor
                                                if (objectCreation.getArguments().size() > 0) {
                                                    Expression resultExpr = objectCreation.getArguments().get(0);
                                                    var.setInitializer(resultExpr);
                                                    modified = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return modified;
    }
}