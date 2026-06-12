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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
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
                .forEach(Main::processFile);
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
            
            // Find all addEnabledLanguages calls on AnalysisEngineConfiguration.Builder
            List<MethodCallExpr> toRemove = new ArrayList<>();
            cu.accept(new MethodCallFinder(), toRemove);
            
            // Remove each found method call
            for (MethodCallExpr mce : toRemove) {
                removeMethodCallFromChain(mce);
            }
            
            if (!toRemove.isEmpty()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static void removeMethodCallFromChain(MethodCallExpr toRemove) {
        // Get the scope of the method call to remove (what comes before it)
        Optional<Expression> scope = toRemove.getScope();
        if (!scope.isPresent()) {
            // No scope, can't remove properly
            return;
        }
        
        // Find if any MethodCallExpr has toRemove as its scope
        // (i.e., if there's a method call after toRemove in the chain)
        Optional<MethodCallExpr> nextInChain = toRemove.getParentNode()
            .flatMap(parent -> parent.getChildNodes().stream()
                .filter(node -> node instanceof MethodCallExpr)
                .map(node -> (MethodCallExpr) node)
                .filter(mce -> mce.getScope().orElse(null) == toRemove)
                .findFirst());
        
        if (nextInChain.isPresent()) {
            // There's a method call after toRemove in the chain
            // Update it to use toRemove's scope instead
            nextInChain.get().setScope(scope.get());
        }
        
        // Now remove toRemove from its parent
        toRemove.remove();
    }
    
    private static class MethodCallFinder extends ModifierVisitor<List<MethodCallExpr>> {
        @Override
        public MethodCallExpr visit(MethodCallExpr n, List<MethodCallExpr> toRemove) {
            // First visit children
            super.visit(n, toRemove);
            
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Check if this is called on AnalysisEngineConfiguration.Builder
                if (isAnalysisEngineConfigurationBuilderCall(n)) {
                    toRemove.add(n);
                }
            }
            return n;
        }
        
        private boolean isAnalysisEngineConfigurationBuilderCall(MethodCallExpr n) {
            // Check if this method is called on AnalysisEngineConfiguration.Builder
            // We'll look for the pattern AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
            
            Optional<Expression> scope = n.getScope();
            if (!scope.isPresent()) {
                return false;
            }
            
            Expression scopeExpr = scope.get();
            String scopeStr = scopeExpr.toString();
            
            // Check if scope contains AnalysisEngineConfiguration
            if (scopeStr.contains("AnalysisEngineConfiguration")) {
                return true;
            }
            
            // Also check if scope is a method call to builder()
            if (scopeExpr instanceof MethodCallExpr) {
                MethodCallExpr scopeCall = (MethodCallExpr) scopeExpr;
                if ("builder".equals(scopeCall.getNameAsString())) {
                    // Check if the builder() call is on AnalysisEngineConfiguration
                    Optional<Expression> builderScope = scopeCall.getScope();
                    return builderScope.map(s -> s.toString().contains("AnalysisEngineConfiguration")).orElse(false);
                }
            }
            
            return false;
        }
    }
}