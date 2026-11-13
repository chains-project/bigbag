package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation rule for fixing Maven dependency API breaking changes
        // Specifically targeting the replacement of Maven31DependencyGraphBuilder with DefaultDependencyGraphBuilder
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        try {
            // Process all Java files in the directory recursively
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file);
            }
            
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create a visitor to find and replace the problematic constructor
            cu.accept(new DependencyGraphBuilderVisitor(), null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class DependencyGraphBuilderVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr objectCreationExpr, Void arg) {
            super.visit(objectCreationExpr, arg);
            
            // Check if this is creating a Maven31DependencyGraphBuilder
            if (objectCreationExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) objectCreationExpr.getType();
                
                // Check if it's the problematic Maven31DependencyGraphBuilder
                if ("Maven31DependencyGraphBuilder".equals(type.getNameAsString())) {
                    // Replace with DefaultDependencyGraphBuilder
                    type.setName("DefaultDependencyGraphBuilder");
                }
            }
        }
    }
}