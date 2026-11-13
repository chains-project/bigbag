package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            int transformedFiles = transformProject(Paths.get(sourceDir));
            System.out.println("Successfully transformed " + transformedFiles + " file(s)");
        } catch (IOException e) {
            System.err.println("Error transforming project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Recursively transforms all Java files in the given directory.
     * Applies the GHCompare.status -> getStatus() transformation.
     * 
     * @param rootDir the root directory containing Java source files
     * @return number of files transformed
     * @throws IOException if file operations fail
     */
    private static int transformProject(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(rootDir)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        
        int transformedCount = 0;
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                FieldAccessToGetterVisitor visitor = new FieldAccessToGetterVisitor();
                cu.accept(visitor, null);
                
                if (visitor.wasModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Transformed: " + javaFile);
                    transformedCount++;
                }
            } catch (IOException e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        return transformedCount;
    }
    
    /**
     * Visitor that transforms field accesses of the form 'expression.status' 
     * to method calls 'expression.getStatus()'.
     * 
     * This handles the specific breaking change in GitHub API 1.314 where
     * GHCompare.status field was made private and replaced with getStatus() method.
     */
    private static class FieldAccessToGetterVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a field access for 'status' field
            if (n.getNameAsString().equals("status")) {
                // Create method call expression: expression.getStatus()
                MethodCallExpr methodCall = new MethodCallExpr(n.getScope(), "getStatus");
                
                modified = true;
                return methodCall;
            }
            
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}