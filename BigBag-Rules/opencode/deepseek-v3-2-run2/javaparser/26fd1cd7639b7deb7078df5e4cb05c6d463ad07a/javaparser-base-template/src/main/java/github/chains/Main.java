package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
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

/**
 * Generic transformation rule for ScriptResult API migration.
 * 
 * This rule handles the breaking change from:
 * - Old: com.gargoylesoftware.htmlunit.ScriptResult(Object result)
 * - New: org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult(String result)
 * 
 * Transformation steps:
 * 1. Replace import declarations
 * 2. Transform constructor calls to use String parameters
 * 3. Replace method calls from getJavaScriptResult() to toString()
 */
public class Main {
    
    // Configuration parameters - these can be changed for different dependency migrations
    private static final String OLD_CLASS_NAME = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_CLASS_NAME = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String OLD_METHOD_NAME = "getJavaScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        CompilationUnit modifiedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            Files.writeString(filePath, modifiedCu.toString());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms ScriptResult API usage patterns
     */
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Replace old class import with new class import
            if (importName.equals(OLD_CLASS_NAME)) {
                modified = true;
                return new ImportDeclaration(
                    StaticJavaParser.parseName(NEW_CLASS_NAME),
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is a ScriptResult constructor call
            if (expr.getType().asString().equals("ScriptResult")) {
                NodeList<Expression> arguments = expr.getArguments();
                
                // Transform Object parameter to String parameter
                if (arguments.size() == 1) {
                    Expression argument = arguments.get(0);
                    
                    // If argument is not already a String, wrap it with .toString()
                    if (!isStringType(argument)) {
                        modified = true;
                        MethodCallExpr toStringCall = new MethodCallExpr(argument, "toString");
                        arguments.set(0, toStringCall);
                    }
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            // Replace getJavaScriptResult() calls with .toString()
            if (expr.getNameAsString().equals(OLD_METHOD_NAME)) {
                modified = true;
                
                // Create a new method call to toString() on the same scope
                return new MethodCallExpr(expr.getScope().orElse(null), "toString");
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        /**
         * Simple heuristic to check if an expression represents a String type
         */
        private boolean isStringType(Expression expr) {
            if (expr instanceof StringLiteralExpr) {
                return true;
            }
            
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                String methodName = methodCall.getNameAsString();
                // Common String-returning methods
                return methodName.equals("toString") || 
                       methodName.equals("getText") || 
                       methodName.equals("asText");
            }
            
            return false;
        }
    }
}