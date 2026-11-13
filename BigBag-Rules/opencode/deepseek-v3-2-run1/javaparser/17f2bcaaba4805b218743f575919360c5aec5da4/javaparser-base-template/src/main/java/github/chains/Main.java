package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }

        System.out.println("Scanning for Java files in: " + sourceDir);
        
        List<File> javaFiles = Files.walk(sourceDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());

        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int modifiedFiles = 0;
        
        for (File file : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                TinspinIndexesTransformer transformer = new TinspinIndexesTransformer();
                CompilationUnit modifiedCu = (CompilationUnit) cu.accept(transformer, null);
                
                if (transformer.wasModified()) {
                    try {
                        modifiedCu.clone();
                        // Write the modified compilation unit back to the file
                        java.nio.file.Files.write(file.toPath(), modifiedCu.toString().getBytes());
                        modifiedFiles++;
                        System.out.println("Modified: " + file.getPath());
                    } catch (Exception e) {
                        System.err.println("Error writing file: " + file.getPath() + " - " + e.getMessage());
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("Error reading file: " + file.getPath());
            }
        }
        
        System.out.println("\nTransformation complete. Modified " + modifiedFiles + " files.");
    }
    
    static class TinspinIndexesTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            // Rule 1: Fix KDTree.create(int, PointDistanceFunction) deprecation
            if (methodName.equals("create") && n.getArguments().size() == 2) {
                Expression secondArg = n.getArgument(1);
                if (isPointDistanceFunction(secondArg)) {
                    System.out.println("[Rule 1] Fixing KDTree.create(dimensions, distanceFunction) deprecation:");
                    System.out.println("  Original: " + n.toString());
                    
                    n.getArguments().remove(1);
                    modified = true;
                    
                    System.out.println("  Modified: " + n.toString());
                    System.out.println("  Note: Use distance functions in query methods (knnQuery) instead.");
                }
            }
            
            // Rule 2: Could be extended for other deprecated methods like knnQuery()
            // if (methodName.equals("knnQuery") && n.getArguments().size() == 2) {
            //     // Handle knnQuery(double[], int) deprecation
            // }
            
            return super.visit(n, arg);
        }
        
        private boolean isPointDistanceFunction(Expression expr) {
            String exprStr = expr.toString();
            // Heuristic: PointDistanceFunctions are typically lambdas or method references
            return exprStr.contains("->") ||                // Lambda expression
                   exprStr.contains("::") ||                // Method reference
                   exprStr.contains("PointDistanceFunction") || // Type reference
                   exprStr.contains("L1") || exprStr.contains("L2"); // Built-in distance functions
        }
    }
}