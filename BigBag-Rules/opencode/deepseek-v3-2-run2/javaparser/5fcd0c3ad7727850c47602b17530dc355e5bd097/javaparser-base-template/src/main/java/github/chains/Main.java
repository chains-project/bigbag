package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these should be parameterized for different breaking changes
    private static final String OLD_METHOD_NAME = "getClassInfo";
    private static final String NEW_METHOD_NAME = "getTestsForClass";
    private static final String TARGET_TYPE_NAME = "org.pitest.coverage.CoverageDatabase";
    
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
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                int transformations = processFile(javaFile);
                if (transformations > 0) {
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("Transformed " + transformations + " occurrences in " + javaFile);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total transformations: " + totalTransformations);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + filePath)
        );
        
        ApiTransformationVisitor visitor = new ApiTransformationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.getTransformationCount() > 0) {
            // Write the transformed file back
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String transformedCode = printer.print(cu);
            Files.write(filePath, transformedCode.getBytes());
        }
        
        return visitor.getTransformationCount();
    }
    
    static class ApiTransformationVisitor extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is the method call we want to transform
            if (isTargetMethodCall(n)) {
                // Transform the method call
                MethodCallExpr transformed = transformMethodCall(n);
                transformationCount++;
                return transformed;
            }
            return super.visit(n, arg);
        }
        
        private boolean isTargetMethodCall(MethodCallExpr methodCall) {
            // Check if method name matches
            if (!methodCall.getNameAsString().equals(OLD_METHOD_NAME)) {
                return false;
            }
            
            // Check if we have exactly one argument (Set<ClassName>)
            if (methodCall.getArguments().size() != 1) {
                return false;
            }
            
            // Note: We cannot easily check the receiver type at parse time without symbol resolution
            // This transformation assumes the method is called on CoverageDatabase objects
            // In practice, this would need symbol resolution or could be parameterized
            return true;
        }
        
        private MethodCallExpr transformMethodCall(MethodCallExpr original) {
            // Extract the single argument (which should be a Set<ClassName>)
            Expression argument = original.getArgument(0);
            
            // Create new method call with the same receiver but new method name
            MethodCallExpr transformed = new MethodCallExpr(
                original.getScope().orElse(null),
                NEW_METHOD_NAME
            );
            
            // For the transformation from getClassInfo(Set<ClassName>) to getTestsForClass(ClassName),
            // we need to extract the single element from the Set.
            // This handles patterns like Collections.singleton(className)
            transformed.addArgument(extractSingleElementFromSet(argument));
            
            return transformed;
        }
        
        private Expression extractSingleElementFromSet(Expression setExpression) {
            // Handle common patterns for creating single-element sets:
            // - Collections.singleton(expr)
            // - Collections.singletonSet(expr) 
            // - Set.of(expr) (Java 9+)
            // - new HashSet<>(Arrays.asList(expr))
            // - etc.
            
            // For now, we'll handle the most common case: Collections.singleton(expr)
            // In a more sophisticated implementation, we would use symbol resolution
            // to identify the actual method being called and extract the argument
            
            if (setExpression instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) setExpression;
                String methodName = methodCall.getNameAsString();
                
                // Check for Collections.singleton and similar methods
                if (methodName.equals("singleton") || methodName.equals("singletonSet")) {
                    // Extract the single argument from the singleton call
                    if (methodCall.getArguments().size() == 1) {
                        return methodCall.getArgument(0);
                    }
                }
            }
            
            // If we can't extract a single element, we'll need to handle this differently
            // For now, we'll return the original expression and let the compilation fail
            // A more sophisticated implementation would need to analyze the Set expression
            System.err.println("Warning: Could not extract single element from Set expression: " + setExpression);
            return setExpression;
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
    }
}