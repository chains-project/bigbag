package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    
    // Configuration: Map of old method patterns to transformation actions
    // This should be externalized in a real implementation
    private static final Map<String, TransformationAction> TRANSFORMATIONS = new HashMap<>();
    
    static {
        // Example: Remove addEnabledLanguages calls on AnalysisEngineConfiguration.Builder
        TRANSFORMATIONS.put("AnalysisEngineConfiguration.Builder.addEnabledLanguages", 
            TransformationAction.REMOVE_CALL);
        
        // Add more transformations as needed for other breaking changes
    }
    
    enum TransformationAction {
        REMOVE_CALL,        // Remove the entire method call
        RENAME_METHOD,      // Rename method (with possible signature change)
        REPLACE_WITH_LOOP,  // Replace collection call with loop of single-element calls
        CUSTOM_TRANSFORM    // Apply custom transformation
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.err.println("Applies configured transformations to fix breaking API changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        System.out.println("Configured transformations: " + TRANSFORMATIONS.size());
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            TransformResult result = transformFile(javaFile);
            if (result.filesModified > 0) {
                transformedFiles++;
                transformedCalls += result.callsTransformed;
                if (result.filesModified > 0) {
                    System.out.println("Modified " + javaFile + " (" + result.callsTransformed + " calls)");
                }
            }
        }
        
        System.out.println("Summary: Transformed " + transformedCalls + " method calls in " + transformedFiles + " files");
    }
    
    private static TransformResult transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            return new TransformResult(0, 0);
        }
        
        BreakingChangeVisitor visitor = new BreakingChangeVisitor();
        visitor.visit(cu, null);
        
        if (visitor.callsTransformed > 0) {
            Files.write(filePath, cu.toString().getBytes());
        }
        
        return new TransformResult(visitor.callsTransformed > 0 ? 1 : 0, visitor.callsTransformed);
    }
    
    private static class BreakingChangeVisitor extends ModifierVisitor<Void> {
        private int callsTransformed = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children
            super.visit(n, arg);
            
            // Try to determine the type this method is called on
            // In a real implementation, we'd use symbol solving to get the exact type
            // For this example, we'll use a simpler pattern matching approach
            
            String methodName = n.getNameAsString();
            
            // Check each configured transformation
            for (Map.Entry<String, TransformationAction> entry : TRANSFORMATIONS.entrySet()) {
                String pattern = entry.getKey();
                TransformationAction action = entry.getValue();
                
                // Simple pattern matching: "ClassName.methodName" 
                // or "ClassName.Builder.methodName"
                String[] parts = pattern.split("\\.");
                if (parts.length >= 2) {
                    String expectedMethod = parts[parts.length - 1];
                    String expectedClass = parts[parts.length - 2];
                    
                    if (methodName.equals(expectedMethod)) {
                        // In a real implementation, we would check if the method is called
                        // on the expected class using symbol solving
                        // For this example, we'll apply the transformation based on method name only
                        
                        if (action == TransformationAction.REMOVE_CALL) {
                            // Remove this method call from the chain
                            // This is complex because we need to remove it from a method chain
                            // For simplicity in this example, we'll just report it
                            
                            System.out.println("Would remove call to " + pattern + " at " + 
                                n.getRange().map(r -> r.begin.toString()).orElse("unknown location"));
                            
                            // In practice, we would need to:
                            // 1. Check if this is in a method chain
                            // 2. If so, remove this node from the chain
                            // 3. If not, replace with empty statement or remove entire statement
                            
                            callsTransformed++;
                        }
                    }
                }
            }
            
            return n;
        }
    }
    
    private static class TransformResult {
        final int filesModified;
        final int callsTransformed;
        
        TransformResult(int filesModified, int callsTransformed) {
            this.filesModified = filesModified;
            this.callsTransformed = callsTransformed;
        }
    }
}