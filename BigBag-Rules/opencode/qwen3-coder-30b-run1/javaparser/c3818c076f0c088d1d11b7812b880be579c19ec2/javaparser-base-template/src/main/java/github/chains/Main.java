package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
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
        
        String sourceDirectory = args[0];
        processDirectory(sourceDirectory);
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + directoryPath);
            return;
        }
        
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformations
            new ValidationImportTransformer().visit(cu, null);
            new ValidationAnnotationTransformer().visit(cu, null);
            
            // Print the modified content
            String modifiedContent = LexicalPreservingPrinter.print(cu);
            
            // Write back to file
            Files.write(filePath, modifiedContent.getBytes());
            
        } catch (Exception e) {
            // Skip files that can't be parsed, but continue processing others
            // System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    // Transformer for javax.validation imports to jakarta.validation
    private static class ValidationImportTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Check if this is a javax.validation import
            if (importDecl.getNameAsString().startsWith("javax.validation")) {
                // Replace with jakarta.validation
                importDecl.setName(importDecl.getNameAsString().replace("javax.validation", "jakarta.validation"));
            }
        }
    }
    
    // Transformer for @Valid annotations
    private static class ValidationAnnotationTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(NormalAnnotationExpr annotation, Void arg) {
            super.visit(annotation, arg);
            
            // Check if this is a @Valid annotation
            if ("Valid".equals(annotation.getNameAsString())) {
                // Change to jakarta.validation.Valid
                annotation.setName("jakarta.validation.Valid");
            }
        }
        
        @Override
        public void visit(MarkerAnnotationExpr annotation, Void arg) {
            super.visit(annotation, arg);
            
            // Check if this is a @Valid annotation
            if ("Valid".equals(annotation.getNameAsString())) {
                // Change to jakarta.validation.Valid
                annotation.setName("jakarta.validation.Valid");
            }
        }
        
        @Override
        public void visit(SingleMemberAnnotationExpr annotation, Void arg) {
            super.visit(annotation, arg);
            
            // Check if this is a @Valid annotation
            if ("Valid".equals(annotation.getNameAsString())) {
                // Change to jakarta.validation.Valid
                annotation.setName("jakarta.validation.Valid");
            }
        }
    }
}