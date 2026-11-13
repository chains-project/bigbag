package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JavaParser transformation to fix breaking changes from HtmlUnit ScriptResult removal.
 * This transformation:
 * 1. Removes imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replaces "new ScriptResult(result)" with direct use of "result"
 * 3. Replaces "scriptResult.getJavaScriptResult()" with direct use of the result object
 * 
 * This is a generic transformation that can be applied to any project affected by the same
 * breaking dependency update where HtmlUnit ScriptResult was replaced with direct Object returns.
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_SCRIPT_RESULT_CLASS = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT_METHOD = "getJavaScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation will fix HtmlUnit ScriptResult breaking changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
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
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(rootDir)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            String transformedContent = transformedCu.toString();
            Files.writeString(javaFile, transformedContent);
            System.out.println("Modified: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms HtmlUnit ScriptResult usage to direct Object usage.
     * This is a generic transformation that works for any project using the old HtmlUnit API.
     */
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            if (importDecl.getNameAsString().equals(OLD_SCRIPT_RESULT_CLASS)) {
                modified = true;
                return null; // Remove this import
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Replace "new ScriptResult(result)" with just "result"
            if (expr.getType().asString().equals("ScriptResult")) {
                // Check if this is a ScriptResult constructor call
                if (expr.getArguments().size() == 1) {
                    Expression argument = expr.getArguments().get(0);
                    modified = true;
                    return argument; // Replace with the argument
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            // Replace "scriptResult.getJavaScriptResult()" with "scriptResult"
            if (expr.getNameAsString().equals(GET_JAVASCRIPT_RESULT_METHOD)) {
                // Check if this is called on a ScriptResult object
                if (expr.getScope().isPresent()) {
                    Expression scope = expr.getScope().get();
                    modified = true;
                    return scope; // Replace with the scope (the ScriptResult object)
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(VariableDeclarationExpr expr, Void arg) {
            // Handle variable declarations like "ScriptResult scriptResult = new ScriptResult(result)"
            // Replace "ScriptResult" type with "Object" type
            expr.getVariables().forEach(variable -> {
                if (variable.getType().asString().equals("ScriptResult")) {
                    variable.setType(new ClassOrInterfaceType(null, "Object"));
                    modified = true;
                }
            });
            return (Node) super.visit(expr, arg);
        }
    }
}