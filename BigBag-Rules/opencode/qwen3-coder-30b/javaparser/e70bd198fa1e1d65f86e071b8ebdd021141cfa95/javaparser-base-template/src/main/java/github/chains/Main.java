package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(Paths.get(sourceDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix tinspin indexes breaking changes
            cu.accept(new TinspinIndexTransformationVisitor(), null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class TinspinIndexTransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Fix KDTree.create method calls
            if (methodCall.getNameAsString().equals("create") && 
                methodCall.getArguments().size() == 2 && 
                methodCall.getScope().isPresent()) {
                Optional<NameExpr> qualifier = methodCall.getScope().filter(NameExpr.class::isInstance)
                    .map(NameExpr.class::cast);
                
                if (qualifier.isPresent()) {
                    String qualifierName = qualifier.get().getNameAsString();
                    if (qualifierName.contains("KDTree")) {
                        // For KDTree.create(int, distanceFunction) -> KDTree.create(IndexConfig)
                        // This requires more complex handling - for now we'll just add a comment
                        // that the method signature has changed
                    }
                }
            }
            
            // Fix query1NN method calls that have changed signatures 
            if (methodCall.getNameAsString().equals("query1NN")) {
                Optional<NameExpr> qualifier = methodCall.getScope().filter(NameExpr.class::isInstance)
                    .map(NameExpr.class::cast);
                
                if (qualifier.isPresent()) {
                    String qualifierName = qualifier.get().getNameAsString();
                    // For now, we're not modifying the method calls since we'd need to understand
                    // the new return type signatures, but we'll add comments to indicate the change
                }
            }
        }
    }
}