package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Generic JavaParser transformation to fix breaking dependency updates in Maven plugins.
 * This fixes the issue where doxia-site-renderer changed the location of RenderingContext class.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try {
            // Process all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(javaFilePath.toFile()));
            
            // Apply transformation to fix doxia RenderingContext import issue
            FixRenderingContextImportVisitor visitor = new FixRenderingContextImportVisitor();
            visitor.visit(cu, null);
            
            System.out.println("Processed: " + javaFilePath);
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix the wrong import and constructor calls for RenderingContext
     */
    private static class FixRenderingContextImportVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Check if this is the incorrect import that needs to be replaced
            if (importDecl.getNameAsString().equals("org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext")) {
                // Replace with correct import
                importDecl.setName("org.apache.maven.doxia.siterenderer.RenderingContext");
                System.out.println("Fixed import: " + importDecl.getNameAsString());
            }
        }
        
        @Override
        public void visit(ObjectCreationExpr objectCreationExpr, Void arg) {
            super.visit(objectCreationExpr, arg);
            
            // Check if this creates a RenderingContext with the old constructor signature
            if (objectCreationExpr.getType().getNameAsString().equals("RenderingContext")) {
                Optional<Expression> scopeOpt = objectCreationExpr.getScope();
                
                // If scope is not present or is not the right class, it's likely the constructor we need to fix
                if (!scopeOpt.isPresent() || 
                   (scopeOpt.get() instanceof FieldAccessExpr &&
                    ((FieldAccessExpr) scopeOpt.get()).getNameAsString().contains("doxia"))) {
                    
                    // Try to detect if it's the wrong signature
                    NodeList<Expression> arguments = objectCreationExpr.getArguments();
                    
                    // If it's the old wrong constructor with 2 parameters, we need to fix it to 3 parameters
                    // The new constructor takes (File, String, String) instead of (File, String)
                    if (arguments.size() == 2) {
                        // Add extra parameter to match new constructor signature
                        // We'll add an empty string for the third parameter
                        arguments.add(new com.github.javaparser.ast.expr.StringLiteralExpr(""));
                        System.out.println("Fixed RenderingContext constructor call");
                    }
                }
            }
        }
    }
}
