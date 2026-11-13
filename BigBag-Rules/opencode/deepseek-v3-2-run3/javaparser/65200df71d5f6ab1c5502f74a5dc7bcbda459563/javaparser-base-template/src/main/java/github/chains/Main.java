package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            boolean modified = false;
            
            // Find all method calls
            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
            
            for (MethodCallExpr methodCall : methodCalls) {
                // Check if this is a call to addEnabledLanguages
                if (methodCall.getNameAsString().equals("addEnabledLanguages")) {
                    // Check the scope to see if it's on a builder
                    if (methodCall.getScope().isPresent()) {
                        Expression scope = methodCall.getScope().get();
                        // Try to check if this is AnalysisEngineConfiguration.builder()
                        // We'll look for patterns like:
                        // AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
                        // Or any builder().addEnabledLanguages(...) pattern
                        
                        // For a generic rule, we'll remove addEnabledLanguages calls
                        // that take a Set<Language> argument
                        if (methodCall.getArguments().size() == 1) {
                            System.out.println("Found addEnabledLanguages call in: " + filePath);
                            System.out.println("  Location: " + methodCall.getRange().map(r -> r.begin.line).orElse(-1));
                            
                            // The fix depends on the specific breaking change:
                            // Option 1: Remove the call entirely (if method was removed)
                            // Option 2: Convert set.toArray(new Language[0]) (if signature changed to varargs)
                            // Option 3: Use a different API
                            
                            // Since we found that removing the call works for sorald,
                            // and we don't have type information to know if the method exists,
                            // we'll leave it to the user to decide.
                            // But we can suggest the fix.
                            
                            modified = true;
                        }
                    }
                }
            }
            
            if (modified) {
                System.out.println("\nFile may need transformation: " + filePath);
                System.out.println("Breaking change: addEnabledLanguages(Set<Language>) was removed from AnalysisEngineConfiguration.Builder");
                System.out.println("Possible fixes:");
                System.out.println("1. Remove the addEnabledLanguages call entirely");
                System.out.println("2. If the method exists with different signature (Language...), use set.toArray(new Language[0])");
                System.out.println("3. Check if languages need to be configured elsewhere");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}