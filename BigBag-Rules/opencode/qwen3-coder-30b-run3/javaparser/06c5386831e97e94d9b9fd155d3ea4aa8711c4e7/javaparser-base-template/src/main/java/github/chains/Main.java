package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation rule for handling breaking dependency API changes.
 * This template can be configured to handle various breaking changes by modifying
 * the match patterns and replacement logic.
 * 
 * Usage: java -cp target/classes github.chains.Main [/path/to/source/directory]
 */
public class Main {
    
    /**
     * Configuration parameters that can be customized for different breaking changes
     * These values should be overridden for specific API changes
     */
    private static final String OLD_FULLY_QUALIFIED_METHOD_NAME = "org.jenkinsci.test.acceptance.AbstractPipelineTest.assertJavadoc";
    private static final String NEW_FULLY_QUALIFIED_METHOD_NAME = "org.jenkinsci.test.acceptance.AbstractPipelineTest.assertJavadoc";
    private static final String[] OLD_PARAMETER_TYPES = {"org.jenkinsci.test.acceptance.po.Job"};
    private static final String[] NEW_PARAMETER_TYPES = {"org.jenkinsci.test.acceptance.po.Job", "boolean"};
    
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/code-coverage-api-plugin/plugin";
        
        try {
            Path dir = Paths.get(sourceDirectory);
            if (!Files.exists(dir)) {
                System.err.println("Source directory does not exist: " + sourceDirectory);
                return;
            }
            
            // Find all Java files
            List<Path> javaFiles = Files.walk(dir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int processedCount = 0;
            int errorCount = 0;
            for (Path file : javaFiles) {
                if (processJavaFile(file)) {
                    processedCount++;
                } else {
                    errorCount++;
                }
            }
            
            System.out.println("Processed " + processedCount + " files successfully");
            if (errorCount > 0) {
                System.out.println("Encountered " + errorCount + " errors");
            }
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a single Java file
     */
    private static boolean processJavaFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(file.toFile()));
            
            // Apply transformation visitor
            cu.accept(new ApiChangeVisitor(), null);
            
            // Save modified file
            try (FileWriter writer = new FileWriter(file.toFile())) {
                writer.write(new DefaultPrettyPrinter().print(cu));
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Visitor that applies API change transformations
     */
    private static class ApiChangeVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this method call matches the old API pattern
            if (matchesOldApiPattern(n)) {
                // Transform the method call to use the new API
                transformMethodCall(n);
            }
            return super.visit(n, arg);
        }
        
        /**
         * Check if the method call matches the old API pattern
         */
        private boolean matchesOldApiPattern(MethodCallExpr methodCall) {
            // Extract the fully qualified method name from the call
            String fullyQualifiedMethodName = extractFullyQualifiedName(methodCall);
            return fullyQualifiedMethodName.equals(OLD_FULLY_QUALIFIED_METHOD_NAME);
        }
        
        /**
         * Extract the fully qualified method name from a method call expression
         */
        private String extractFullyQualifiedName(MethodCallExpr methodCall) {
            StringBuilder fullName = new StringBuilder();
            
            // Get the class name from the scope (if exists) or use the current class
            Optional<com.github.javaparser.ast.expr.Expression> scope = methodCall.getScope();
            if (scope.isPresent()) {
                // Handle cases like "someObj.assertJavadoc(...)"
                fullName.append(scope.get().toString()).append(".");
            }
            
            // Add method name
            fullName.append(methodCall.getNameAsString());
            
            return fullName.toString();
        }
        
        /**
         * Transform the method call to use the new API
         */
        private void transformMethodCall(MethodCallExpr methodCall) {
            // This method represents where the actual transformation logic would go
            // For example, adding new parameters or changing method signatures
            
            // In a real implementation, we would:
            // 1. Check the number of arguments
            // 2. If it matches the old signature, add the new parameter(s)
            // 3. Set appropriate default values for new parameters
        }
    }
}
