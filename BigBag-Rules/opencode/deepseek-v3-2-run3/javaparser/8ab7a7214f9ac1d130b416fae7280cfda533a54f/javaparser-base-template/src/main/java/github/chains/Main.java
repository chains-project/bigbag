package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <oldFqcn> <newFqcn>");
            System.err.println("Example: java github.chains.Main /path/to/src com.gargoylesoftware.htmlunit.ScriptResult org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult");
            System.err.println("\nThis transformation handles:");
            System.err.println("1. Changing imports from oldFqcn to newFqcn");
            System.err.println("2. Changing constructor calls");
            System.err.println("3. Removing getJavaScriptResult() method calls on ScriptResult objects");
            System.err.println("4. Converting Object arguments to String for new constructor");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldFqcn = args[1];
        String newFqcn = args[2];
        
        try {
            transformProject(sourceDir, oldFqcn, newFqcn);
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String oldFqcn, String newFqcn) throws IOException {
        List<Path> javaFiles = findAllJavaFiles(sourceDir);
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile, oldFqcn, newFqcn);
        }
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static void transformFile(Path javaFile, String oldFqcn, String newFqcn) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
            new RuntimeException("Failed to parse " + javaFile));
        
        // Create visitor to transform the code
        ScriptResultTransformer transformer = new ScriptResultTransformer(oldFqcn, newFqcn);
        cu.accept(transformer, null);
        
        // Write back the transformed file
        Files.write(javaFile, cu.toString().getBytes());
    }
    
    static class ScriptResultTransformer extends ModifierVisitor<Void> {
        private final String oldFqcn;
        private final String newFqcn;
        private final String oldSimpleName;
        private final String newSimpleName;
        
        public ScriptResultTransformer(String oldFqcn, String newFqcn) {
            this.oldFqcn = oldFqcn;
            this.newFqcn = newFqcn;
            this.oldSimpleName = oldFqcn.substring(oldFqcn.lastIndexOf('.') + 1);
            this.newSimpleName = newFqcn.substring(newFqcn.lastIndexOf('.') + 1);
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this is the old import
            if (importName.equals(oldFqcn)) {
                // Replace with new import
                importDecl.setName(newFqcn);
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call for the old class
            if (n.getType().toString().equals(oldSimpleName) || n.getType().toString().equals(newSimpleName)) {
                // Ensure we're using the new class name
                n.setType(newSimpleName);
                
                // The new constructor takes String instead of Object
                // If we have one argument that's not already converted, wrap with .toString()
                if (n.getArguments().size() == 1) {
                    Expression argExpr = n.getArguments().get(0);
                    // Only add .toString() if not already a method call ending with .toString()
                    if (!(argExpr instanceof MethodCallExpr && 
                          ((MethodCallExpr) argExpr).getName().asString().equals("toString"))) {
                        // Create method call: arg.toString()
                        MethodCallExpr toStringCall = new MethodCallExpr(argExpr, "toString");
                        n.getArguments().set(0, toStringCall);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Remove calls to getJavaScriptResult() on ScriptResult objects
            if (n.getName().asString().equals("getJavaScriptResult")) {
                System.err.println("Found getJavaScriptResult() call: " + n);
                // Check if the scope is a ScriptResult object creation
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    String scopeStr = scope.toString();
                    System.err.println("Scope: " + scopeStr);
                    
                    // Check if this looks like: new ScriptResult(...).getJavaScriptResult()
                    // or scriptResultVariable.getJavaScriptResult()
                    boolean isScriptResultRef = scopeStr.contains(oldSimpleName) || 
                                               scopeStr.contains(newSimpleName) ||
                                               (scope instanceof ObjectCreationExpr && 
                                                ((ObjectCreationExpr) scope).getType().toString().equals(newSimpleName));
                    
                    System.err.println("isScriptResultRef: " + isScriptResultRef);
                    if (isScriptResultRef) {
                        System.err.println("Removing getJavaScriptResult()");
                        // Replace the entire expression with just the scope
                        // This removes .getJavaScriptResult()
                        return scope;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}