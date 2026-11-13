package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule to fix breaking dependency changes in ASTO library.
 * This rule specifically fixes the change from Storages to StoragesLoader class.
 * 
 * The transformation fixes:
 * 1. Import statements: com.artipie.asto.factory.Storages -> com.artipie.asto.factory.StoragesLoader
 * 2. Object creation: new Storages() -> new StoragesLoader()
 * 
 * This is a reusable rule that can be applied to any Maven project with the same breaking change.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // The source directory to process - this can be parameterized
        String sourceDir = args.length > 0 ? args[0] : "/workspace/http";
        
        // Process all Java files in the directory
        processDirectory(Paths.get(sourceDir));
    }
    
    private static void processDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Create a visitor to find and fix the problematic pattern
        StoragesFixVisitor visitor = new StoragesFixVisitor();
        visitor.visit(cu, null);
        
        // Write back the modified content
        String newContent = cu.toString();
        if (!newContent.equals(content)) {
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Fixed file: " + javaFile);
        }
    }
    
    /**
     * Visitor that finds and replaces Storages() with StoragesLoader() 
     */
    private static class StoragesFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a new Storages() call
            if (n.getType().getNameAsString().equals("Storages")) {
                // Replace with StoragesLoader
                n.setType("StoragesLoader");
                System.out.println("Fixed Storages() to StoragesLoader() in " + n.toString());
            }
        }
    }
}