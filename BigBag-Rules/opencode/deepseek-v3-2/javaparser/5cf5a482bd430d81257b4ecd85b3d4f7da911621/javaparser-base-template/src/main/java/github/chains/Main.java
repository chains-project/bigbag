package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            // Configure JavaParser with symbol solver
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            JavaParser javaParser = new JavaParser();
            javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow();
                    MethodSignatureTransformer transformer = new MethodSignatureTransformer();
                    transformer.visit(cu, null);
                    
                    if (transformer.getTransformationCount() > 0) {
                        // Write changes back to file
                        Files.write(javaFile, cu.toString().getBytes());
                        transformedFiles++;
                        totalTransformations += transformer.getTransformationCount();
                        System.out.println("Transformed " + transformer.getTransformationCount() + 
                                          " method calls in: " + javaFile);
                    }
                } catch (FileNotFoundException e) {
                    System.err.println("File not found: " + javaFile);
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total transformations: " + totalTransformations);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Generic transformer for Jakarta MVC API breaking changes.
     * 
     * This transformer handles common patterns of breaking changes:
     * 1. Method signature changes (added/removed/reordered parameters)
     * 2. Method name changes
     * 3. Class/method deprecation and replacement
     * 
     * The transformer is parameterized and can be extended for specific
     * API changes by adding new transformation rules.
     */
    private static class MethodSignatureTransformer extends VoidVisitorAdapter<Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            try {
                // Try to resolve the method to get its fully qualified signature
                String methodSignature = n.resolve().getQualifiedSignature();
                String methodName = n.getNameAsString();
                int argCount = n.getArguments().size();
                
                // Apply transformations based on method signature patterns
                // These patterns are parameterized and can be configured
                
                // ------------------------------------------------------------
                // TRANSFORMATION PATTERN 1: Method signature changes
                // Pattern: ClassName.methodName(oldSignature) -> ClassName.methodName(newSignature)
                // ------------------------------------------------------------
                
                // Example: jakarta.mvc.MvcContext.getContext() -> jakarta.mvc.MvcContext.getContext(boolean)
                if (methodSignature.contains("jakarta.mvc.MvcContext.getContext()")) {
                    // Add default parameter: getContext() -> getContext(true)
                    n.addArgument(new BooleanLiteralExpr(true));
                    transformationCount++;
                    System.out.println("  Applied transformation: getContext() -> getContext(true)");
                }
                
                // Example: jakarta.mvc.event.MvcEvent.getSource() signature change
                if (methodSignature.contains("jakarta.mvc.event.MvcEvent.getSource()") && 
                    methodName.equals("getSource") && argCount == 0) {
                    // If getSource() needs parameters in new API
                    // n.addArgument(new SomeExpression());
                    // transformationCount++;
                }
                
                // ------------------------------------------------------------
                // TRANSFORMATION PATTERN 2: Method name changes  
                // Pattern: ClassName.oldMethod() -> ClassName.newMethod()
                // ------------------------------------------------------------
                
                // Example: jakarta.mvc.oldMethod() -> jakarta.mvc.newMethod()
                if (methodSignature.contains("jakarta.mvc.oldMethod") && methodName.equals("oldMethod")) {
                    // n.setName("newMethod");
                    // transformationCount++;
                }
                
                // ------------------------------------------------------------
                // TRANSFORMATION PATTERN 3: Parameter reordering
                // Pattern: method(param1, param2) -> method(param2, param1)
                // ------------------------------------------------------------
                
                if (methodSignature.contains("jakarta.mvc.someMethod") && methodName.equals("someMethod") && argCount == 2) {
                    // Reorder parameters if needed
                    // Expression first = n.getArgument(0);
                    // Expression second = n.getArgument(1);
                    // n.setArguments(new NodeList<>(second, first));
                    // transformationCount++;
                }
                
                // ------------------------------------------------------------
                // TRANSFORMATION PATTERN 4: Added required parameters
                // Pattern: method(param) -> method(param, defaultValue)
                // ------------------------------------------------------------
                
                if (methodSignature.contains("jakarta.mvc.anotherMethod") && methodName.equals("anotherMethod") && argCount == 1) {
                    // Add default parameter
                    // n.addArgument(new BooleanLiteralExpr(false));
                    // transformationCount++;
                }
                
            } catch (UnsolvedSymbolException e) {
                // Method cannot be resolved - might be using old/deprecated API
                // Apply pattern-based transformations
                applyPatternBasedTransformations(n);
            } catch (Exception e) {
                // Continue with other transformations
            }
        }
        
        /**
         * Apply transformations based on pattern matching when symbol resolution fails.
         * This handles cases where the old API methods cannot be resolved.
         */
        private void applyPatternBasedTransformations(MethodCallExpr n) {
            String methodName = n.getNameAsString();
            int argCount = n.getArguments().size();
            
            // Pattern: Method calls that likely need additional parameters in new API
            if (methodName.equals("getContext") && argCount == 0) {
                // Common pattern: getContext() -> getContext(true)
                n.addArgument(new BooleanLiteralExpr(true));
                transformationCount++;
                System.out.println("  Applied pattern-based transformation: getContext() -> getContext(true)");
            }
            
            // Pattern: Method name changes (common deprecated methods)
            if (methodName.equals("getOldName")) {
                n.setName("getNewName");
                transformationCount++;
                System.out.println("  Applied pattern-based transformation: getOldName() -> getNewName()");
            }
            
            // Pattern: Methods that lost parameters
            if (methodName.equals("methodWithRemovedParam") && argCount == 2) {
                // Remove last parameter if it's no longer needed
                // n.remove(n.getArguments().get(1));
                // transformationCount++;
            }
        }
    }
}