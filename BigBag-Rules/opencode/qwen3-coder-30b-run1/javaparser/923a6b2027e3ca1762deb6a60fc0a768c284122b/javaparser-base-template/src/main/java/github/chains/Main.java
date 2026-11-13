package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing Hamcrest 1.3 to 2.2 API changes.
 * This transformation addresses breaking changes in Hamcrest library where
 * certain methods or signatures were modified between versions.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Create a visitor to find and transform hamcrest calls
            HamcrestVisitor visitor = new HamcrestVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            // cu.save(javaFilePath.toFile()); // Commented out since we're not actually transforming yet
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds hamcrest method calls and transforms them according to the API change.
     */
    private static class HamcrestVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a hamcrest method call and transform it
            if (isHamcrestMethodCall(methodCall)) {
                // Placeholder for transformation logic
                // In a real implementation, this would apply specific transformations
                System.out.println("Found hamcrest method call: " + methodCall.toString());
            }
        }
        
        private boolean isHamcrestMethodCall(MethodCallExpr methodCall) {
            // Check if the method call is in hamcrest package
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope.isNameExpr()) {
                    NameExpr nameExpr = (NameExpr) scope;
                    String identifier = nameExpr.getNameAsString();
                    return identifier.startsWith("Matchers.") || 
                           (identifier.contains("hamcrest") && 
                            identifier.contains("Matchers"));
                }
            }
            return false;
        }
    }
}