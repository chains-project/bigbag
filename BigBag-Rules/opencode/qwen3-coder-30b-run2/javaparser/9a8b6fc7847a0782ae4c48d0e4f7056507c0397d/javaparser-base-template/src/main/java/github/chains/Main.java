package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Files.walk(Paths.get(dirPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation
            TransformationVisitor visitor = new TransformationVisitor();
            cu.accept(visitor, null);
            
            // Save changes back to file
            try (PrintWriter out = new PrintWriter(new FileWriter(filePath.toString()))) {
                out.print(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class TransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Transform jasypt 1.9.2 breaking changes
            String typeName = n.getType().toString();
            
            // Handle deprecated org.jasypt.spring.security.PasswordEncoder creation
            if (typeName.contains("org.jasypt.spring.security.PasswordEncoder")) {
                // Replace with direct implementation (StrongPasswordEncryptor)
                ObjectCreationExpr replacement = new ObjectCreationExpr();
                replacement.setType("org.jasypt.util.password.StrongPasswordEncryptor");
                return replacement;
            }
            
            // Handle deprecated org.jasypt.spring.security.PBEPasswordEncoder creation
            if (typeName.contains("org.jasypt.spring.security.PBEPasswordEncoder")) {
                // Replace with direct implementation (StandardPBEStringEncryptor)
                ObjectCreationExpr replacement = new ObjectCreationExpr();
                replacement.setType("org.jasypt.encryption.pbe.StandardPBEStringEncryptor");
                return replacement;
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Transform references to deprecated jasypt packages
            String exprStr = n.toString();
            
            // Replace deprecated package references
            if (exprStr.contains("org.jasypt.spring.security")) {
                // This would need to be handled based on specific usages in the code
                // But the main change is in object creation, not field access
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(NameExpr n, Void arg) {
            // Transform direct references to deprecated classes
            String exprStr = n.toString();
            
            // Replace deprecated class references
            if (exprStr.contains("org.jasypt.spring.security.PasswordEncoder")) {
                // This shouldn't appear as a NameExpr in most cases
                // Just return unchanged for now
            }
            
            return super.visit(n, arg);
        }
    }
}