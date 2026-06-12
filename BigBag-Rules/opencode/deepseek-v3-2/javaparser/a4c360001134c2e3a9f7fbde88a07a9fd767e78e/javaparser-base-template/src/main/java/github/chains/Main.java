package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming code in: " + sourceDir);
        
        // Simple parser without symbol resolution for broader compatibility
        JavaParser javaParser = new JavaParser();
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                FilterBuilderApplyToTestVisitor visitor = new FilterBuilderApplyToTestVisitor();
                visitor.visit(cu, null);
                
                if (visitor.getTransformedCount() > 0) {
                    // Write transformed file
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedCalls += visitor.getTransformedCount();
                    System.out.println("Transformed " + javaFile + " (" + visitor.getTransformedCount() + " calls)");
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformation complete.");
        System.out.println("Files transformed: " + transformedFiles);
        System.out.println("Total method calls transformed: " + transformedCalls);
    }
    
    /**
     * Visitor that transforms FilterBuilder.apply(String) calls to FilterBuilder.test(String)
     * 
     * This is a generic transformation rule for the breaking change in Reflections library 0.10.2
     * where FilterBuilder.apply(String) was replaced with FilterBuilder.test(String).
     * 
     * The rule matches:
     * - Any method call with name "apply" and exactly one argument
     * - Where the scope (receiver) shows patterns of FilterBuilder usage
     * 
     * This heuristic approach works without type resolution, making it more portable
     * and applicable to any project affected by this breaking change.
     */
    private static class FilterBuilderApplyToTestVisitor extends VoidVisitorAdapter<Void> {
        private int transformedCount = 0;
        
        public int getTransformedCount() {
            return transformedCount;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if method name is "apply"
            if (!"apply".equals(n.getNameAsString())) {
                return;
            }
            
            // Check if it has exactly one argument (typical for FilterBuilder.apply(String))
            if (n.getArguments().size() != 1) {
                return;
            }
            
            // Heuristic pattern matching for FilterBuilder usage
            boolean shouldTransform = false;
            
            if (n.getScope().isPresent()) {
                String scopeCode = n.getScope().get().toString();
                
                // Pattern 1: Direct FilterBuilder instantiation
                if (scopeCode.contains("new FilterBuilder()") || scopeCode.contains("FilterBuilder(")) {
                    shouldTransform = true;
                }
                // Pattern 2: Include/exclude patterns (common FilterBuilder methods)
                else if (scopeCode.contains(".include(") || scopeCode.contains(".exclude(") ||
                        scopeCode.contains(".includePackage(") || scopeCode.contains(".excludePackage(") ||
                        scopeCode.contains(".includePattern(") || scopeCode.contains(".excludePattern(") ||
                        scopeCode.contains(".add(") || scopeCode.contains(".parsePackages(")) {
                    shouldTransform = true;
                }
                // Pattern 3: Variable or method that likely returns FilterBuilder
                else if (scopeCode.contains("FilterBuilder")) {
                    shouldTransform = true;
                }
            }
            
            // Also check the full expression as fallback
            if (!shouldTransform) {
                String fullExpression = n.toString();
                if (fullExpression.contains("FilterBuilder") && 
                    (fullExpression.contains(".include(") || fullExpression.contains(".exclude("))) {
                    shouldTransform = true;
                }
            }
            
            if (shouldTransform) {
                n.setName("test");
                transformedCount++;
            }
        }
    }
}