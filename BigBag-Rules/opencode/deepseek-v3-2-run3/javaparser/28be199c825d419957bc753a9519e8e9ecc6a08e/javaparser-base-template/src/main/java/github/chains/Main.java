package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for API breaking changes where method signatures change.
 * 
 * Example: Maven Dependency Tree API breaking change
 * - Old API: DependencyGraphBuilder.buildDependencyGraph(MavenProject project, ArtifactFilter filter)
 * - New API: DependencyGraphBuilder.buildDependencyGraph(ProjectBuildingRequest buildingRequest, ArtifactFilter filter)
 * 
 * Transformation: Replace method call's first argument with a configurable replacement.
 * This is a structural pattern transformation that can be adapted for similar breaking changes.
 */
public class Main {
    
    // Configurable parameters - these demonstrate the pattern for this specific breaking change
    // In a fully generic tool, these would be command-line arguments or configuration files
    private static final String TARGET_METHOD = "buildDependencyGraph";
    private static final String REPLACEMENT_PATTERN = "getBuildingRequest()"; // Simple method call pattern
    
    // Additional configuration for a fully generic tool would include:
    // - Old parameter type pattern to match (e.g., "org.apache.maven.project.MavenProject")
    // - Replacement strategy (method call, constructor, field access, etc.)
    // - Scope analysis to find appropriate replacement in context
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms calls to " + TARGET_METHOD + " by replacing first argument");
            System.err.println("Replacement pattern: " + REPLACEMENT_PATTERN);
            System.err.println("");
            System.err.println("For different breaking changes, modify:");
            System.err.println("  - TARGET_METHOD: The method name to transform");
            System.err.println("  - REPLACEMENT_PATTERN: The expression to replace first argument with");
            System.err.println("");
            System.err.println("Example breaking change pattern:");
            System.err.println("  Old: SomeClass.method(OldType arg1, OtherType arg2)");
            System.err.println("  New: SomeClass.method(NewType arg1, OtherType arg2)");
            System.err.println("  Transformation: Replace OldType expression with NewType expression");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming " + TARGET_METHOD + " calls in: " + sourceDir);
        System.out.println("Replacement pattern: first argument -> " + REPLACEMENT_PATTERN);
        
        JavaParser javaParser = new JavaParser();
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files to analyze");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                MethodCallTransformer transformer = new MethodCallTransformer();
                cu.accept(transformer, null);
                
                if (transformer.getTransformationCount() > 0) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedCalls += transformer.getTransformationCount();
                    System.out.println("  Transformed " + transformer.getTransformationCount() + 
                                     " call(s) in: " + sourceDir.relativize(javaFile));
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Successfully transformed " + transformedCalls + " method call(s) in " + transformedFiles + " file(s)");
    }
    
    /**
     * Visitor that transforms method calls matching the target pattern.
     * Looks for calls to buildDependencyGraph and replaces the first argument.
     */
    private static class MethodCallTransformer extends VoidVisitorAdapter<Void> {
        private int transformationCount = 0;
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a call to our target method
            if (n.getNameAsString().equals(TARGET_METHOD)) {
                // We need at least 2 arguments for this pattern
                if (n.getArguments().size() >= 2) {
                    // Parse the replacement pattern to create appropriate AST node
                    // For simple patterns like "getBuildingRequest()" we create a MethodCallExpr
                    // For more complex patterns, we would need a full parser
                    try {
                        // Simple heuristic: if pattern ends with "()", treat as method call
                        if (REPLACEMENT_PATTERN.endsWith("()")) {
                            String methodName = REPLACEMENT_PATTERN.substring(0, REPLACEMENT_PATTERN.length() - 2);
                            MethodCallExpr replacement = new MethodCallExpr();
                            replacement.setName(methodName);
                            n.getArgument(0).replace(replacement);
                            transformationCount++;
                            
                            System.out.println("    Applied transformation at line: " + 
                                             n.getRange().map(r -> r.begin.line).orElse(-1) +
                                             " (replaced with: " + REPLACEMENT_PATTERN + ")");
                        } else {
                            // For other patterns, we could use JavaParser to parse the expression
                            // This would handle field accesses, constructor calls, etc.
                            System.err.println("    Could not parse replacement pattern: " + REPLACEMENT_PATTERN);
                        }
                    } catch (Exception e) {
                        System.err.println("    Error applying transformation: " + e.getMessage());
                    }
                }
            }
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
    }
}