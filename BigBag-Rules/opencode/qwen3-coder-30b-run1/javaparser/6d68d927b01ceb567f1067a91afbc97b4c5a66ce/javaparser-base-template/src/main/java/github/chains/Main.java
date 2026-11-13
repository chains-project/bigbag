package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/code-coverage-api-plugin";
        processDirectory(Paths.get(sourceDir));
    }

    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to fix ScriptResult usage
            ScriptResultFixer fixer = new ScriptResultFixer();
            cu.accept(fixer, null);
            
            // Write back the modified content
            try (PrintWriter out = new PrintWriter(filePath.toString())) {
                out.println(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Visitor to find and fix ScriptResult usage patterns in executeScript calls
     */
    public static class ScriptResultFixer extends ModifierVisitor<Void> {
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            // Look for: new ScriptResult(result).getJavaScriptResult()
            // Replace with: result
            if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                // Check if this is a call on a ScriptResult object
                if (methodCall.getScope().isPresent()) {
                    Node scope = methodCall.getScope().get();
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                        if (creation.getType().toString().contains("ScriptResult")) {
                            // Return the argument of ScriptResult constructor instead
                            NodeList<Expression> args = creation.getArguments();
                            if (args.size() > 0) {
                                return args.get(0);
                            }
                        }
                    }
                }
            }
            
            return methodCall;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            importDecl = (ImportDeclaration) super.visit(importDecl, arg);
            
            // Remove imports for ScriptResult
            if (importDecl.getNameAsString().contains("ScriptResult")) {
                return null; // Remove the import
            }
            
            return importDecl;
        }
    }
}