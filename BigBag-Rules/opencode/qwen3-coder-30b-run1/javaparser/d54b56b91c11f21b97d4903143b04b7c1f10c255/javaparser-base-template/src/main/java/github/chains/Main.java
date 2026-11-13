package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
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
            
            // Create a visitor to find and fix the problematic code patterns
            FixPubSubLiteApiChangesVisitor visitor = new FixPubSubLiteApiChangesVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            LexicalPreservingPrinter.setup(cu);
            Files.write(filePath, cu.toString().getBytes());
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix PubSubLite API changes in the codebase
     * Specifically fixes:
     * 1. Removes references to PublishMetadata class (no longer exists)
     * 2. Removes calls to setContext method (no longer exists)
     */
    private static class FixPubSubLiteApiChangesVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Remove imports for PublishMetadata if they exist
            if (importDecl.getNameAsString().contains("PublishMetadata")) {
                importDecl.remove();
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
            
            // Check for PublisherFactory interface and remove PublishMetadata references
            if (classDecl.getNameAsString().equals("PublisherFactory")) {
                classDecl.getMethods().forEach(method -> {
                    if (method.getNameAsString().equals("newPublisher")) {
                        // Remove PublishMetadata from return type - replace with just Publisher
                        method.setType("Publisher");
                    }
                });
            }
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Remove calls to setContext method
            if (methodCall.getNameAsString().equals("setContext")) {
                // Find the parent statement and remove it
                if (methodCall.getParentNode().isPresent()) {
                    methodCall.getParentNode().get().ifInstanceOf(ExpressionStmt.class, stmt -> stmt.remove());
                }
            }
        }
    }
}