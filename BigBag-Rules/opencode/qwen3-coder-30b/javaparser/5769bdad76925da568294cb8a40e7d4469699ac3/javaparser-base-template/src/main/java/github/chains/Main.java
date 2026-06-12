package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
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

/**
 * Generic transformation rule to fix GHCompare.status field access
 * in GitHub API 1.313+ where status field became private and requires getter method.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(String directory) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file);
            }
        }
    }
    
    private static void processFile(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        GHCompareStatusFixer visitor = new GHCompareStatusFixer();
        cu.accept(visitor, null);
        
        if (visitor.isChanged()) {
            // Write the modified content back to the file
            Files.write(file, cu.toString().getBytes());
            System.out.println("Fixed: " + file);
        }
    }
    
    /**
     * Visitor that transforms field access to method calls for GHCompare.status
     */
    private static class GHCompareStatusFixer extends ModifierVisitor<Void> {
        private boolean changed = false;
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing .status on a GHCompare object
            if (n.getNameAsString().equals("status")) {
                // Check if it's a method call on getCompare that returns GHCompare
                if (n.getScope() instanceof MethodCallExpr) {
                    MethodCallExpr scope = (MethodCallExpr) n.getScope();
                    if (scope.getNameAsString().equals("getCompare")) {
                        // This looks like: repository.getCompare(branch, hash).status
                        // We need to transform it to: repository.getCompare(branch, hash).getStatus()
                        changed = true;
                        return new MethodCallExpr(scope, "getStatus");
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        public boolean isChanged() {
            return changed;
        }
    }
}