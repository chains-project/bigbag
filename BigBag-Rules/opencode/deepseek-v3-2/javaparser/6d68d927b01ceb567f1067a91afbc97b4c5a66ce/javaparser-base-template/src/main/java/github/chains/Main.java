package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p));
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            final boolean[] modified = new boolean[1];
            
            // Check for old import
            List<ImportDeclaration> imports = cu.getImports();
            ImportDeclaration[] oldImport = new ImportDeclaration[1];
            for (ImportDeclaration imp : imports) {
                if (imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    oldImport[0] = imp;
                    modified[0] = true;
                    System.out.println("Found old ScriptResult import in: " + filePath);
                }
            }
            
            // First pass: find and replace all getJavaScriptResult() method calls
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    
                    if (n.getNameAsString().equals("getJavaScriptResult")) {
                        // Check if this is called on a variable of type ScriptResult
                        // or on a new ScriptResult() expression
                        if (n.getScope().isPresent()) {
                            Expression scope = n.getScope().get();
                            
                            // Case 1: scope is a variable reference
                            // We'll handle this by removing the call entirely
                            // x.getJavaScriptResult() -> x
                            n.replace(scope);
                            modified[0] = true;
                            System.out.println("Removed getJavaScriptResult() call in: " + filePath);
                        }
                    }
                }
            }, null);
            
            // Second pass: find and replace all ScriptResult constructor calls
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr n, Void arg) {
                    super.visit(n, arg);
                    
                    // Check if this is a ScriptResult constructor call
                    if (n.getType().asString().equals("ScriptResult")) {
                        // Get the argument expression
                        Expression argExpr = n.getArguments().get(0);
                        
                        // Replace new ScriptResult(arg) with arg
                        n.replace(argExpr.clone());
                        modified[0] = true;
                        System.out.println("Replaced new ScriptResult(...) with argument in: " + filePath);
                    }
                }
            }, null);
            
            // Third pass: find and fix variable declarations with ScriptResult type
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(com.github.javaparser.ast.body.VariableDeclarator n, Void arg) {
                    super.visit(n, arg);
                    
                    // Check if variable type is ScriptResult
                    if (n.getType().asString().equals("ScriptResult")) {
                        // Change type to Object
                        n.setType(new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "Object"));
                        modified[0] = true;
                        System.out.println("Changed ScriptResult variable type to Object in: " + filePath);
                    }
                }
            }, null);
            
            // Remove the old import if we modified the file
            if (oldImport[0] != null) {
                cu.remove(oldImport[0]);
                System.out.println("Removed old import in: " + filePath);
            }
            
            if (modified[0]) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Updated file: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}