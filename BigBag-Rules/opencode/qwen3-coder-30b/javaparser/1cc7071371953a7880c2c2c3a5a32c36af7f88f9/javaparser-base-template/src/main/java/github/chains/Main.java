package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        // Find all Java files in the source directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to remove byte-buddy dependencies
            new ByteBuddyRemovalVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that removes byte-buddy related code and imports
     */
    private static class ByteBuddyRemovalVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Remove imports related to byte-buddy
            if (importDecl.getNameAsString().startsWith("org.assertj.core.internal.bytebuddy")) {
                importDecl.remove();
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
            
            // Remove any method declarations that reference byte-buddy types
            classDecl.getMethods().removeIf(method -> {
                // Check if method name or any parameter contains bytebuddy
                return method.getNameAsString().contains("bytebuddy") ||
                       method.getParameters().stream().anyMatch(p -> 
                           p.getType().asString().contains("bytebuddy"));
            });
        }
        
        @Override
        public void visit(MethodDeclaration methodDecl, Void arg) {
            super.visit(methodDecl, arg);
            
            // Remove any method body that contains byte-buddy code
            if (methodDecl.getBody().isPresent()) {
                BlockStmt body = methodDecl.getBody().get();
                body.getStatements().removeIf(statement -> {
                    return statement.toString().contains("bytebuddy");
                });
            }
        }
    }
}