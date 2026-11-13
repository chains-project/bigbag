package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(new File(sourceDir));
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (File file : javaFiles) {
                JavaParser javaParser = new JavaParser();
                CompilationUnit cu = javaParser.parse(file).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + file));
                
                boolean modified = false;
                
                // Remove ScriptResult imports
                List<ImportDeclaration> importsToRemove = new ArrayList<>();
                cu.getImports().forEach(importDecl -> {
                    if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        importsToRemove.add(importDecl);
                    }
                });
                
                if (!importsToRemove.isEmpty()) {
                    importsToRemove.forEach(cu::remove);
                    modified = true;
                    System.out.println("  Removed ScriptResult import from: " + file);
                }
                
                // Transform ScriptResult constructor calls
                ScriptResultTransformer transformer = new ScriptResultTransformer();
                cu.accept(transformer, null);
                
                if (transformer.modified) {
                    modified = true;
                    totalTransformations += transformer.transformCount;
                }
                
                if (modified) {
                    transformedFiles++;
                    // Write back the transformed file
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(cu.toString());
                    } catch (IOException e) {
                        System.err.println("Failed to write file " + file + ": " + e.getMessage());
                    }
                    System.out.println("Transformed: " + file);
                }
            }
            
            System.out.println("\nSummary: Transformed " + transformedFiles + " files with " + totalTransformations + " total transformations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        findJavaFilesRecursive(file, javaFiles);
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        int transformCount = 0;
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is new ScriptResult(...)
            if (expr.getType().getNameAsString().equals("ScriptResult")) {
                Node parent = expr.getParentNode().orElse(null);
                
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) parent;
                    if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                        // Case: new ScriptResult(expr).getJavaScriptResult()
                        // Replace the entire MethodCallExpr with the argument
                        Expression argument = expr.getArguments().get(0);
                        modified = true;
                        transformCount++;
                        
                        // Check if we're in an assignment or expression statement
                        Node grandParent = parent.getParentNode().orElse(null);
                        if (grandParent instanceof VariableDeclarationExpr) {
                            // Handle: Object var = new ScriptResult(expr).getJavaScriptResult();
                            // We need to replace just the initializer
                            return argument;
                        } else {
                            // Replace the method call with the argument
                            return argument;
                        }
                    }
                } else if (parent instanceof VariableDeclarationExpr) {
                    // Case: ScriptResult var = new ScriptResult(expr);
                    // We'll handle this in visit(VariableDeclarationExpr)
                } else {
                    // Case: new ScriptResult(expr) used directly
                    // Replace with expr
                    Expression argument = expr.getArguments().get(0);
                    modified = true;
                    transformCount++;
                    return argument;
                }
            }
            
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr expr, Void arg) {
            // Check if declaring a ScriptResult variable
            for (var variable : expr.getVariables()) {
                if (variable.getType().toString().equals("ScriptResult")) {
                    // Get the initializer if present
                    Expression initializer = variable.getInitializer().orElse(null);
                    if (initializer instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) initializer;
                        if (creation.getType().getNameAsString().equals("ScriptResult")) {
                            // Replace ScriptResult var = new ScriptResult(expr) with Object var = expr
                            Expression argument = creation.getArguments().get(0);
                            modified = true;
                            transformCount++;
                            variable.setType("Object");
                            variable.setInitializer(argument);
                        }
                    }
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            // Check if this is scriptResultVar.getJavaScriptResult()
            if (expr.getNameAsString().equals("getJavaScriptResult")) {
                Expression scope = expr.getScope().orElse(null);
                if (scope instanceof NameExpr) {
                    // Could be a variable of type ScriptResult
                    // We should handle this by replacing with the variable itself
                    // since getJavaScriptResult() would return the wrapped value
                    // Actually, if we see x.getJavaScriptResult() and x was a ScriptResult,
                    // we should have already transformed x's declaration
                    // So x should now be of type Object, and getJavaScriptResult() shouldn't exist
                    // We'll leave this case for manual fixing or a more sophisticated transformer
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}