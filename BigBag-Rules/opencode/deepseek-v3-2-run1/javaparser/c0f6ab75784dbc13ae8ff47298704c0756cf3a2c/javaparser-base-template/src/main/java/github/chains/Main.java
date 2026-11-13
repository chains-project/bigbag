package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
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
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This transformation fixes: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)");
            System.err.println("The method was removed in the new API version.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        JavaParser parser = new JavaParser();
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                String originalContent = new String(Files.readAllBytes(javaFile));
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                final boolean[] wasModified = new boolean[]{false};
                
                // First pass: find and fix the specific pattern
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Look for calls to addEnabledLanguages
                        if (n.getNameAsString().equals("addEnabledLanguages")) {
                            // Check if it's called on AnalysisEngineConfiguration.builder()
                            String callStr = n.toString();
                            if (callStr.contains("AnalysisEngineConfiguration.builder()")) {
                                System.out.println("Found target in " + javaFile);
                                
                                // We need to remove this method call from the chain
                                // If it has a parent that's also a MethodCallExpr, we're in a chain
                                if (n.getParentNode().isPresent() && 
                                    n.getParentNode().get() instanceof MethodCallExpr) {
                                    
                                    MethodCallExpr parent = (MethodCallExpr) n.getParentNode().get();
                                    // The parent is the next method in the chain (e.g., .setClientPid())
                                    // We need to make parent be called on n's scope instead of on n
                                    
                                    if (n.getScope().isPresent()) {
                                        // n's scope is what n was called on (e.g., AnalysisEngineConfiguration.builder())
                                        Expression scope = n.getScope().get();
                                        // Set parent to be called on that scope instead
                                        parent.setScope(scope);
                                        wasModified[0] = true;
                                        System.out.println("  Removed addEnabledLanguages from chain");
                                    }
                                } else {
                                    // This is a standalone call or end of chain
                                    // We should replace the entire expression with just the scope
                                    // But we need to check what contains this expression
                                    System.out.println("  WARNING: addEnabledLanguages at end of chain or standalone - may need manual fix");
                                    wasModified[0] = true;
                                }
                            }
                        }
                    }
                }, null);
                
                if (wasModified[0]) {
                    // Write back the modified file
                    String newContent = cu.toString();
                    // Simple formatting fix - restore some readability
                    newContent = newContent.replaceAll("\\.\\s*\\n\\s*", ".\n                ");
                    Files.write(javaFile, newContent.getBytes());
                    transformedFiles++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
        System.out.println("\nIMPORTANT: This is a generic transformation for the breaking change:");
        System.out.println("  Old: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)");
        System.out.println("  New: Method removed (enabled languages likely inherited from global config)");
        System.out.println("\nThe transformation removes the method call from builder chains.");
        System.out.println("Manual review may be needed to ensure correctness.");
    }
}