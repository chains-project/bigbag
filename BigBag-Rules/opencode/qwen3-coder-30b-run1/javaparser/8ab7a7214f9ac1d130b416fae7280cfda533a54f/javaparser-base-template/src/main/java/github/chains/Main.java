package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        // Load the source code
        String sourcePath = args[0]; // First argument is the source directory
        File sourceDir = new File(sourcePath);
        
        // Find all Java files
        Files.walkFileTree(sourceDir.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    try {
                        // Parse the file
                        CompilationUnit cu = StaticJavaParser.parse(file.toFile());
                        
                        // Visit and transform the AST
                        cu.accept(new ScriptResultFixer(), null);
                        
                        // Write the modified file back
                        Files.write(file, cu.toString().getBytes());
                    } catch (Exception e) {
                        System.err.println("Error processing " + file + ": " + e.getMessage());
                        // Don't stop on error, continue processing other files
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static class ScriptResultFixer extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(final ImportDeclaration importDecl, final Object arg) {
            // Remove the ScriptResult import
            if (importDecl.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                importDecl.remove();
            }
            super.visit(importDecl, arg);
        }

        @Override
        public void visit(final VariableDeclarator vd, final Object arg) {
            // Look for: ScriptResult result = new ScriptResult(...);
            if (vd.getType().isClassOrInterfaceType() && 
                vd.getType().asClassOrInterfaceType().getName().asString().equals("ScriptResult") &&
                vd.getInitializer().isPresent()) {
                
                Expression init = vd.getInitializer().get();
                if (init.isObjectCreationExpr()) {
                    ObjectCreationExpr oce = init.asObjectCreationExpr();
                    
                    // Check if it's creating a ScriptResult with a single argument
                    if (oce.getType().getName().asString().equals("ScriptResult") &&
                        oce.getArguments().size() == 1) {
                        
                        // Replace the variable declaration
                        // Original: ScriptResult result = new ScriptResult(expression);
                        // New: Object result = expression;
                        
                        // Get the expression passed to ScriptResult constructor
                        Expression argExpr = oce.getArguments().get(0);
                        
                        // Change the type from ScriptResult to Object
                        vd.setType(new ClassOrInterfaceType("Object"));
                        
                        // Remove the ObjectCreationExpr and use the argument directly
                        vd.setInitializer(argExpr);
                    }
                }
            }
            super.visit(vd, arg);
        }
    }
}