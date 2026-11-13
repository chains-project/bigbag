package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            int transformedFiles = transformProject(sourceDir);
            System.out.println("Transformation complete. Modified " + transformedFiles + " file(s).");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformProject(String sourceDir) throws Exception {
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        int transformedCount = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                EnableLoggingRemover visitor = new EnableLoggingRemover();
                cu.accept(visitor, null);
                
                if (visitor.wasModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedCount++;
                    System.out.println("Modified: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        return transformedCount;
    }
    
    private static class EnableLoggingRemover extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to enableLogging method
            if ("enableLogging".equals(n.getNameAsString())) {
                // Check if this method call is the entire expression statement
                // by checking if parent is ExpressionStmt
                if (n.getParentNode().isPresent() && 
                    n.getParentNode().get() instanceof ExpressionStmt) {
                    modified = true;
                    // Remove the entire statement by returning null
                    return null;
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}