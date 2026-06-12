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

public class RobustMain {
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
                .forEach(RobustMain::processFile);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String originalContent = new String(Files.readAllBytes(filePath));
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + filePath)
            );
            
            cu.accept(new RemoveAddEnabledLanguagesVisitor(), null);
            
            String modifiedContent = cu.toString();
            if (!originalContent.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class RemoveAddEnabledLanguagesVisitor extends ModifierVisitor<Void> {
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // First visit children
            super.visit(n, arg);
            
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Check if this is called on AnalysisEngineConfiguration.Builder
                if (isAnalysisEngineConfigurationBuilderCall(n)) {
                    // Get the scope (what comes before .addEnabledLanguages)
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent()) {
                        // Replace this node with its scope
                        // This effectively removes addEnabledLanguages from the chain
                        return scope.get().clone();
                    }
                }
            }
            return n;
        }
        
        private boolean isAnalysisEngineConfigurationBuilderCall(MethodCallExpr n) {
            // Simple check: look for AnalysisEngineConfiguration in the scope
            return n.getScope()
                .map(scope -> scope.toString().contains("AnalysisEngineConfiguration"))
                .orElse(false);
        }
    }
}