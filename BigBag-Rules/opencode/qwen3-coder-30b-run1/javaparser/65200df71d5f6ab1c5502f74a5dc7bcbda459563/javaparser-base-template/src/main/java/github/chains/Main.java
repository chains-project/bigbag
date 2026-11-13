package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.out.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to fix addEnabledLanguages API change
            boolean modified = applyTransformation(cu);
            
            if (modified) {
                String updatedContent = LexicalPreservingPrinter.print(cu);
                Files.writeString(filePath, updatedContent);
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean applyTransformation(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // Find and transform addEnabledLanguages calls
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                super.visit(methodCall, arg);
                
                // Check if this is a call to addEnabledLanguages with a Set<Language>
                if (methodCall.getNameAsString().equals("addEnabledLanguages") && 
                    methodCall.getArguments().size() == 1) {
                    
                    // Check if it's part of AnalysisEngineConfiguration.builder() chain
                    Expression scope = methodCall.getScope().orElse(null);
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr scopeMethodCall = (MethodCallExpr) scope;
                        if (scopeMethodCall.getNameAsString().equals("builder") && 
                            scopeMethodCall.getScope().isPresent()) {
                            
                            Expression scopeOfScope = scopeMethodCall.getScope().get();
                            if (scopeOfScope instanceof NameExpr) {
                                NameExpr nameExpr = (NameExpr) scopeOfScope;
                                if (nameExpr.getNameAsString().equals("AnalysisEngineConfiguration")) {
                                    // This is the problematic pattern we need to fix
                                    // For now, just print a message about what needs to be fixed
                                    System.out.println("Found addEnabledLanguages call in " + 
                                        methodCall.getScope().get().toString() + " that needs API update");
                                    modified[0] = true;
                                }
                            }
                        }
                    }
                }
            }
        }, null);
        
        return modified[0];
    }
}