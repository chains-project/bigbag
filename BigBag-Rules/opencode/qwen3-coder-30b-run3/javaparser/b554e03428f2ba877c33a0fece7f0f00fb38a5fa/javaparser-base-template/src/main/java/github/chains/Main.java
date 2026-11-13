package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation rule for fixing Maven dependency tree API breaking changes.
 * Specifically addresses the removal of Maven31DependencyGraphBuilder in version 3.2.0.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory recursively
        String targetDir = args.length > 0 ? args[0] : "/workspace/license-maven-plugin";
        
        // Find all Java files
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(targetDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        
        // Apply transformation to each file
        for (Path file : javaFiles) {
            transformFile(file);
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Visit the AST and find all instances of the broken API
        new DependencyGraphBuilderTransformationVisitor().visit(cu, null);
        
        // Write back the modified content
        Files.writeString(filePath, cu.toString());
    }
    
    /**
     * Visitor that finds and transforms the specific problematic constructor call
     */
    private static class DependencyGraphBuilderTransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(NewClassExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a constructor call to Maven31DependencyGraphBuilder
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("Maven31DependencyGraphBuilder".equals(type.getNameAsString())) {
                    // Replace with DefaultDependencyGraphBuilder
                    type.setName("DefaultDependencyGraphBuilder");
                    System.out.println("Fixed Maven31DependencyGraphBuilder to DefaultDependencyGraphBuilder in " + filePath);
                }
            }
        }
    }
}
    }
    
    private static void transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Visit the AST and find all instances of the broken API
        new DependencyGraphBuilderTransformationVisitor().visit(cu, null);
        
        // Write back the modified content
        Files.writeString(filePath, cu.toString());
    }
    
    /**
     * Visitor that finds and transforms the specific problematic constructor call
     */
    private static class DependencyGraphBuilderTransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(NewClassExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a constructor call to Maven31DependencyGraphBuilder
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("Maven31DependencyGraphBuilder".equals(type.getNameAsString())) {
                    // Replace with DefaultDependencyGraphBuilder
                    // We need to replace the type name with DefaultDependencyGraphBuilder
                    type.setName("DefaultDependencyGraphBuilder");
                    System.out.println("Transformed Maven31DependencyGraphBuilder to DefaultDependencyGraphBuilder in " + 
                        n.getRange().map(r -> r.begin.getLine() + ":" + r.begin.getColumn()).orElse("unknown"));
                }
            }
        }
    }
}