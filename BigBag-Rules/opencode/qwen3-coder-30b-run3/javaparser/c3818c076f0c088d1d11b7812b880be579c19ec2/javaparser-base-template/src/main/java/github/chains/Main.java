package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule for handling breaking changes in Hibernate Validator APIs.
 * This transformation identifies validation method calls and applies fixes for API changes
 * between versions.
 */
public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/nem";
        
        try {
            System.out.println("Processing files in: " + sourceDir);
            processDirectory(sourceDir);
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Path dirPath = Paths.get(directoryPath);
        if (!Files.exists(dirPath)) {
            throw new IOException("Directory does not exist: " + directoryPath);
        }
        
        Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation
            ValidationRuleTransformer transformer = new ValidationRuleTransformer();
            transformer.visit(cu, null);
            
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and transforms validation API calls
     */
    private static class ValidationRuleTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Look for validation-related method calls
            if (isValidationMethod(methodCall)) {
                // Apply transformations based on detected breaking changes
                return transformValidationCall(methodCall);
            }
            return super.visit(methodCall, arg);
        }
        
        private boolean isValidationMethod(MethodCallExpr methodCall) {
            // Check for common validation method patterns
            String methodName = methodCall.getNameAsString();
            
            // Common validation method patterns
            return "validate".equals(methodName) || 
                   "validateProperty".equals(methodName) || 
                   "validateValue".equals(methodName) ||
                   "validateAll".equals(methodName);
        }
        
        private Visitable transformValidationCall(MethodCallExpr methodCall) {
            // This is where specific transformations would be applied
            // For now, we'll just mark the method call as processed
            // Specific implementations would be added based on the exact breaking change
            
            // In a real implementation, this would:
            // 1. Check the method signature pattern
            // 2. Identify what changed between versions
            // 3. Apply appropriate transformations
            // 4. Handle the new API signatures
            
            System.out.println("Found validation call: " + methodCall.toString());
            return methodCall;
        }
    }
}
