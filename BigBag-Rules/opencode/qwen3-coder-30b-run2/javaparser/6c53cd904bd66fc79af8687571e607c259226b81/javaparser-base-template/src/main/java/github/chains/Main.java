package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation to identify breaking changes in ObjectFactory buildInterceptor method signatures.
 * This transformation identifies problematic buildInterceptor method calls and declarations
 * that commonly occur due to dependency updates, particularly in struts2 integration.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
            System.out.println("Analysis completed successfully.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Path startPath = Paths.get(dirPath);
        try (Stream<Path> paths = Files.walk(startPath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file.toFile());
            }
        }
    }
    
    private static void processFile(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply transformation rule to identify buildInterceptor method issues
            cu.accept(new BuildInterceptorAnalyzer(), null);
            
        } catch (Exception e) {
            // Silently ignore parsing errors for non-Java files
        }
    }
    
    /**
     * Visitor that identifies buildInterceptor method declarations and calls
     * for further analysis of breaking changes.
     */
    private static class BuildInterceptorAnalyzer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            // Look for buildInterceptor method declarations
            if ("buildInterceptor".equals(n.getNameAsString())) {
                System.out.println("Found buildInterceptor method declaration");
            }
            super.visit(n, arg);
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            // Look for calls to buildInterceptor
            if ("buildInterceptor".equals(n.getNameAsString())) {
                System.out.println("Found buildInterceptor call");
            }
            super.visit(n, arg);
        }
    }
}