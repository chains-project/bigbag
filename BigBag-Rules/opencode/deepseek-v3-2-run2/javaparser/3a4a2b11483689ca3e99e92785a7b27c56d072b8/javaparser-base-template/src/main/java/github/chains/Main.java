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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing ScriptResult API breaking change.
 * 
 * This transformation handles the migration from:
 * - Old: com.gargoylesoftware.htmlunit.ScriptResult
 * - New: org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 * 
 * Transformation rules:
 * 1. Replace import declaration
 * 2. Replace constructor calls: new ScriptResult(Object) -> new ScriptResult(String)
 * 3. Replace method calls: getJavaScriptResult() -> output(null)
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_IMPORT = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";
    private static final String NEW_METHOD = "output";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
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
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        boolean[] modified = {false};
        
        // Replace imports
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            if (importDecl.getNameAsString().equals(OLD_IMPORT)) {
                importDecl.setName(NEW_IMPORT);
                modified[0] = true;
            }
        }
        
        // Replace constructor calls
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (expr.getType().asString().equals("ScriptResult") && 
                expr.getArguments().size() == 1) {
                
                Expression arg = expr.getArguments().get(0);
                // Convert Object argument to String by calling toString()
                if (!(arg instanceof MethodCallExpr && 
                      ((MethodCallExpr) arg).getNameAsString().equals("toString"))) {
                    
                    MethodCallExpr toStringCall = new MethodCallExpr(arg, "toString");
                    expr.getArguments().set(0, toStringCall);
                    modified[0] = true;
                }
            }
        });
        
        // Replace method calls
        cu.findAll(MethodCallExpr.class).forEach(expr -> {
            if (expr.getNameAsString().equals(OLD_METHOD)) {
                // Replace with output(null) - null can be replaced with appropriate Node if needed
                expr.setName(NEW_METHOD);
                expr.getArguments().clear();
                expr.getArguments().add(new NullLiteralExpr());
                modified[0] = true;
            }
        });
        
        if (modified[0]) {
            String newContent = cu.toString();
            Files.write(filePath, newContent.getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
}