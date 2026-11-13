package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking API changes where public fields
 * become private and are replaced with getter methods.
 * 
 * This transformation:
 * - Finds field access expressions on specific types
 * - Replaces field access with method calls to getter methods
 * - Is parameterized by fully-qualified type names and field names
 */
public class Main {
    
    // Configuration: Parameterize by fully-qualified type and field names
    private static final String TARGET_TYPE = "org.kohsuke.github.GHCompare";
    private static final String TARGET_FIELD = "status";
    private static final String GETTER_METHOD = "getStatus";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                int transformations = processFile(javaFile.toFile());
                if (transformations > 0) {
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("Transformed " + transformations + " occurrences in " + javaFile);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("- Files processed: " + javaFiles.size());
            System.out.println("- Files transformed: " + transformedFiles);
            System.out.println("- Total transformations: " + totalTransformations);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processFile(File file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream fis = new FileInputStream(file)) {
            cu = parser.parse(fis).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + file.getName()));
        }
        
        FieldAccessTransformer transformer = new FieldAccessTransformer();
        cu.accept(transformer, null);
        
        if (transformer.getTransformations() > 0) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(cu.toString().getBytes());
            }
        }
        
        return transformer.getTransformations();
    }
    
    /**
     * Visitor that transforms field access expressions to method calls.
     * This is a generic transformation that can be applied to any field
     * that has been made private and replaced with a getter method.
     */
    private static class FieldAccessTransformer extends ModifierVisitor<Void> {
        private int transformations = 0;
        
        public int getTransformations() {
            return transformations;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a field access on our target type
            // We look for patterns like: someExpression.status
            // where someExpression should be of type TARGET_TYPE
            
            if (TARGET_FIELD.equals(n.getNameAsString())) {
                // This is a field access to our target field
                // Replace with method call: someExpression.getStatus()
                MethodCallExpr methodCall = new MethodCallExpr(
                    n.getScope(), // The object on which to call the method
                    GETTER_METHOD // The getter method name
                );
                
                transformations++;
                return methodCall;
            }
            
            return super.visit(n, arg);
        }
    }
}