package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FinalMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(FinalMain::processFile);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + filePath)
            );
            
            AddEnabledLanguagesRemover remover = new AddEnabledLanguagesRemover();
            cu.accept(remover, null);
            
            if (remover.wasModified()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class AddEnabledLanguagesRemover extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Check if this is called on AnalysisEngineConfiguration.Builder
                if (isCalledOnAnalysisEngineConfigurationBuilder(n)) {
                    modified = true;
                    
                    // Get what comes before .addEnabledLanguages
                    Optional<Expression> scope = n.getScope();
                    if (!scope.isPresent()) {
                        // No scope, can't fix this
                        return n;
                    }
                    
                    // We need to remove this method call from wherever it appears
                    // The simplest approach is to replace it with its scope
                    // But we need to check if there are method calls after this one
                    
                    // Look at the parent node to see if we're in a chain
                    Optional<Node> parent = n.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof MethodCallExpr) {
                        // We're in a chain: parent.method(...).addEnabledLanguages(...).nextMethod(...)
                        // Actually, in a chain A.b().c().d(), the structure is:
                        // - d() has scope = c()
                        // - c() has scope = b()
                        // - b() has scope = A
                        // So if n is c(), then parent would be whatever contains the entire expression
                        // not d(). So this check might not work as expected.
                    }
                    
                    // For now, use a simpler approach: just remove the call
                    // This might break chains, but it's a start
                    return scope.get().clone();
                }
            }
            
            // Visit children
            return super.visit(n, arg);
        }
        
        private boolean isCalledOnAnalysisEngineConfigurationBuilder(MethodCallExpr n) {
            // Simple check: look for AnalysisEngineConfiguration in the scope chain
            Optional<Expression> scope = n.getScope();
            if (!scope.isPresent()) {
                return false;
            }
            
            // Check the scope and its ancestors for AnalysisEngineConfiguration
            Expression current = scope.get();
            while (true) {
                String currentStr = current.toString();
                if (currentStr.contains("AnalysisEngineConfiguration")) {
                    return true;
                }
                
                if (current instanceof MethodCallExpr) {
                    Optional<Expression> nextScope = ((MethodCallExpr) current).getScope();
                    if (nextScope.isPresent()) {
                        current = nextScope.get();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            
            return false;
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}