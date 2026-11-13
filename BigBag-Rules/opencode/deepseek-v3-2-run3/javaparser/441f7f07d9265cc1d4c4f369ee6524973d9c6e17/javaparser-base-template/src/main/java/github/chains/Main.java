package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static boolean transformFile(Path javaFile) throws Exception {
        String content = new String(Files.readAllBytes(javaFile));
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Remove old ScriptResult imports
        List<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration imp = imports.get(i);
            if (imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                cu.remove(imp);
                modified = true;
            }
        }
        
        // Transform ScriptResult usage patterns
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        cu.accept(transformer, null);
        
        if (transformer.modified || modified) {
            String transformed = cu.toString();
            Files.write(javaFile, transformed.getBytes());
            return true;
        }
        
        return false;
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a ScriptResult object creation or variable
                Expression scope = n.getScope().orElse(null);
                if (scope != null) {
                    // If scope is new ScriptResult(...), replace the whole expression with the argument
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                        if (creation.getType().asString().equals("ScriptResult") && 
                            creation.getArguments().size() == 1) {
                            modified = true;
                            return creation.getArguments().get(0);
                        }
                    }
                    // Otherwise, just remove the getJavaScriptResult() call
                    // (e.g., scriptResult.getJavaScriptResult() -> scriptResult)
                    else {
                        modified = true;
                        return scope;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is creating a ScriptResult
            if (n.getType().asString().equals("ScriptResult")) {
                // Get the argument passed to ScriptResult constructor
                if (n.getArguments().size() == 1) {
                    // Check if parent is already handled by visit(MethodCallExpr)
                    // If not, replace new ScriptResult(result) with result
                    Node parent = n.getParentNode().orElse(null);
                    if (!(parent instanceof MethodCallExpr && 
                          ((MethodCallExpr) parent).getNameAsString().equals("getJavaScriptResult"))) {
                        modified = true;
                        return n.getArguments().get(0);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Change ScriptResult type references to Object
            if (n.getNameAsString().equals("ScriptResult")) {
                modified = true;
                return new ClassOrInterfaceType("Object");
            }
            return super.visit(n, arg);
        }
    }
}