package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to fix breaking changes in Maven dependency tree APIs.
 * Specifically addresses the removal of Maven31DependencyGraphBuilder in maven-dependency-tree 3.2.1
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Find all Java files in the source directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            // Parse the Java file
            FileInputStream in = new FileInputStream(filePath.toFile());
            CompilationUnit cu = StaticJavaParser.parse(in);
            in.close();
            
            // Apply transformation
            DependencyGraphFixVisitor visitor = new DependencyGraphFixVisitor();
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
    
    /**
     * Visitor to find and fix Maven31DependencyGraphBuilder references
     */
    private static class DependencyGraphFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Look for new Maven31DependencyGraphBuilder() constructions
            if (n.getType().toString().equals("Maven31DependencyGraphBuilder")) {
                // Replace with DefaultDependencyGraphBuilder
                n.setType("DefaultDependencyGraphBuilder");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(SimpleName n, Void arg) {
            // Handle cases where Maven31DependencyGraphBuilder is referenced as a type name
            if (n.getIdentifier().equals("Maven31DependencyGraphBuilder")) {
                // Replace with DefaultDependencyGraphBuilder
                return new SimpleName("DefaultDependencyGraphBuilder");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Handle class type references
            if (n.getNameAsString().equals("Maven31DependencyGraphBuilder")) {
                n.setName("DefaultDependencyGraphBuilder");
            }
            return super.visit(n, arg);
        }
    }
}