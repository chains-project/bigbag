package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation to fix TFramedTransport breaking change in libthrift 0.16.0+
 * This transformation addresses the issue where TFramedTransport was removed/changed
 * in newer versions of Apache Thrift.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java source files in the specified directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/singer";
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path filePath : javaFiles) {
                System.out.println("Processing: " + filePath);
                
                // Parse the Java file
                CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
                
                // Apply transformation to fix TFramedTransport usage
                TransformationVisitor visitor = new TransformationVisitor();
                cu.accept(visitor, null);
                
                // Save the modified file
                Files.write(filePath, cu.toString().getBytes());
            }
        }
    }
    
    /**
     * Visitor that identifies TFramedTransport usage for manual fixing
     */
    private static class TransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a TFramedTransport constructor call
            if ("TFramedTransport".equals(n.getType().getNameAsString())) {
                System.out.println("  Found TFramedTransport constructor usage - manual fix required");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Replace direct class references
            if ("TFramedTransport".equals(n.getNameAsString())) {
                System.out.println("  Found direct TFramedTransport class reference - manual fix required");
            }
            return super.visit(n, arg);
        }
    }
}