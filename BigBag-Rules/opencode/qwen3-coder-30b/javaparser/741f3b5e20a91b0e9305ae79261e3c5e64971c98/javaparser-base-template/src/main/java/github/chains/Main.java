package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Generic JavaParser transformation rule to fix Jackson StreamReadException issues
 * 
 * This rule addresses the breaking change in Jackson where StreamReadException was moved/removed
 * from com.fasterxml.jackson.core.exc to com.fasterxml.jackson.core
 * 
 * The transformation:
 * 1. Finds all usages of com.fasterxml.jackson.core.exc.StreamReadException
 * 2. Replaces with com.fasterxml.jackson.core.StreamReadException
 * 3. Updates import statements accordingly
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the source directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix Jackson StreamReadException issues
            boolean modified = applyTransformation(cu);
            
            if (modified) {
                // Save the modified file
                cu.save(filePath.toString());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Apply the generic transformation to fix Jackson StreamReadException issues
     */
    private static boolean applyTransformation(CompilationUnit cu) {
        boolean modified = false;
        
        // Find and replace StreamReadException import statements
        modified |= replaceStreamReadExceptionImport(cu);
        
        // Find and replace StreamReadException usage in method calls
        modified |= replaceStreamReadExceptionUsage(cu);
        
        return modified;
    }
    
    /**
     * Replace import statements for StreamReadException
     */
    private static boolean replaceStreamReadExceptionImport(CompilationUnit cu) {
        boolean modified = false;
        
        // Check for import statements that need to be updated
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.equals("com.fasterxml.jackson.core.exc.StreamReadException")) {
                // Replace with the new location
                importDecl.setName("com.fasterxml.jackson.core.StreamReadException");
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * Replace StreamReadException usage in catch blocks and type references
     */
    private static boolean replaceStreamReadExceptionUsage(CompilationUnit cu) {
        boolean modified = false;
        
        // Visitor to find and replace StreamReadException references
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(CatchClause catchClause, Void arg) {
                super.visit(catchClause, arg);
                
                // Check if catch block catches StreamReadException
                if (catchClause.getParameter().getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) catchClause.getParameter().getType();
                    if ("StreamReadException".equals(type.getNameAsString())) {
                        // Update to new package location
                        type.setName("com.fasterxml.jackson.core.StreamReadException");
                        modified = true;
                    }
                }
            }
            
            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                super.visit(type, arg);
                
                // Check for direct StreamReadException type references
                if ("StreamReadException".equals(type.getNameAsString()) && 
                    type.getScope().isPresent() && 
                    "com.fasterxml.jackson.core.exc".equals(type.getScope().get().asString())) {
                    // Update to new package location
                    type.setName("com.fasterxml.jackson.core.StreamReadException");
                    modified = true;
                }
            }
        }, null);
        
        return modified;
    }
}
