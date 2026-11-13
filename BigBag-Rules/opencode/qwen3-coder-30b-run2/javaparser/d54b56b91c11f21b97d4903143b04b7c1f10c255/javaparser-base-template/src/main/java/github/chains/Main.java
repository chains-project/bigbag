package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = System.getProperty("source.dir", "/workspace/java-pubsub-group-kafka-connector");
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find all Java files
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            TransformVisitor visitor = new TransformVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove imports for PublishMetadata 
            if (n.getNameAsString().contains("PublishMetadata")) {
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarator n, Void arg) {
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Remove type arguments that contain PublishMetadata
            if (n.getNameAsString().equals("Publisher")) {
                // We'll handle this by setting to null when we detect we need to remove the type parameter
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Remove setContext calls
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) n.getExpression();
                if (methodCall.getNameAsString().equals("setContext")) {
                    // Remove the entire statement
                    return null;
                }
            }
            return super.visit(n, arg);
        }
    }
}
