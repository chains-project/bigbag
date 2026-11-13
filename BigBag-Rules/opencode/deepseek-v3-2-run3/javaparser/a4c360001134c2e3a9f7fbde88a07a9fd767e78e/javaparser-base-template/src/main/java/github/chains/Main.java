package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            int transformations = transformFile(javaFile);
            if (transformations > 0) {
                transformedFiles++;
                totalTransformations += transformations;
                System.out.println("Transformed " + transformations + " occurrences in " + javaFile);
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static int transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        ApplyToTestVisitor visitor = new ApplyToTestVisitor();
        cu.accept(visitor, null);
        
        if (visitor.getTransformCount() > 0) {
            String transformedCode = cu.toString();
            Files.write(javaFile, transformedCode.getBytes());
        }
        
        return visitor.getTransformCount();
    }
    
    private static class ApplyToTestVisitor extends ModifierVisitor<Void> {
        private int transformCount = 0;
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Pattern: FilterBuilderInstance.apply(String)
            // Check if method name is "apply" and the scope is a FilterBuilder method chain
            if ("apply".equals(methodCall.getNameAsString())) {
                // Check if the method call scope is a MethodCallExpr (chained call)
                if (methodCall.getScope().isPresent() && 
                    methodCall.getScope().get() instanceof MethodCallExpr) {
                    
                    MethodCallExpr scopeCall = (MethodCallExpr) methodCall.getScope().get();
                    
                    // Generic pattern: any method call ending with .apply(String)
                    // We'll rename "apply" to "test" for Predicate interface compatibility
                    methodCall.setName("test");
                    transformCount++;
                }
            }
            
            return super.visit(methodCall, arg);
        }
        
        public int getTransformCount() {
            return transformCount;
        }
    }
}