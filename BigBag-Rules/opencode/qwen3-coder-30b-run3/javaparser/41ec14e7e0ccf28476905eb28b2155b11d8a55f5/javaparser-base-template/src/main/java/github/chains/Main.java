package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileWriter;
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
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            new ValidationImportRemovalVisitor().visit(cu, null);
            new BeanUtilsRemovalVisitor().visit(cu, null);
            
            // Print and save the modified file
            String output = LexicalPreservingPrinter.print(cu);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(output);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that removes javax.validation imports
     */
    private static class ValidationImportRemovalVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Remove javax.validation imports
            if (importDecl.getNameAsString().startsWith("javax.validation")) {
                importDecl.remove();
            }
        }
    }
    
    /**
     * Visitor that handles removal of commons-beanutils PropertyUtils usage
     */
    private static class BeanUtilsRemovalVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Remove org.apache.commons.beanutils imports
            if (importDecl.getNameAsString().startsWith("org.apache.commons.beanutils")) {
                importDecl.remove();
            }
        }
    }
}