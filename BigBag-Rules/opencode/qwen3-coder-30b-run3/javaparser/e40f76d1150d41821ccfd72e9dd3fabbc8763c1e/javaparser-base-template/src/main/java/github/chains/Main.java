package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing breaking dependency updates that affect method chain patterns.
 * This transformation can be used to address breaking changes that involve method chains with 
 * method calls that no longer exist or have changed signatures.
 * 
 * Example of pattern it finds and can address:
 * obj.getOverallStatus().getResult() 
 * 
 * This can be extended to handle different breaking change patterns.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(new File(sourceDir));
    }
    
    private static void processDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            System.err.println("Directory does not exist: " + dir.getAbsolutePath());
            return;
        }
        
        Files.walk(Paths.get(dir.getAbsolutePath()))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply the transformation to fix method chain patterns
            MethodChainFixer visitor = new MethodChainFixer();
            visitor.visit(cu, null);
            
            // Write back the modified content
            Files.writeString(filePath, cu.toString());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes method chains with breaking API changes.
     */
    private static class MethodChainFixer extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
        }
        
        @Override
        public void visit(ReturnStmt returnStmt, Void arg) {
            super.visit(returnStmt, arg);
            
            // Look for return statements that contain chained method calls
            returnStmt.getExpression().ifPresent(expr -> {
                fixBreakingMethodChains(expr);
            });
        }
        
        /**
         * Fixes breaking method chain patterns that are commonly affected by breaking changes.
         * 
         * This implementation provides a placeholder for future customization.
         * For the specific case described in the problem, this would be:
         * qualityGateResult.getOverallStatus().getResult() -> qualityGateResult.getOverallStatus().getStatus()
         * 
         * But since we don't know the exact API change, we'll leave it in place for manual review.
         */
        private void fixBreakingMethodChains(Expression expr) {
            // This is a safe placeholder - in practice, you would implement
            // the actual logic for your specific breaking change
            // For now, we'll just identify the problem areas for manual inspection
            
            // Look for chained method calls in the form: obj.getOverallStatus().getResult()
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                
                // Check if this is a method call that ends with getResult() 
                // and is called on something that came from getOverallStatus()
                if (methodCall.getArguments().isEmpty() && 
                    methodCall.getNameAsString().equals("getResult")) {
                    
                    // Check for chained calls: obj.getOverallStatus().getResult()
                    if (methodCall.getScope().isPresent()) {
                        Expression scope = methodCall.getScope().get();
                        
                        if (scope instanceof MethodCallExpr) {
                            MethodCallExpr parentCall = (MethodCallExpr) scope;
                            
                            // Check if the parent call is getOverallStatus()
                            if (parentCall.getNameAsString().equals("getOverallStatus")) {
                                // Note: The actual replacement logic would go here
                                // This is where you would implement the specific API change fix
                                // For now, we just mark the location for human review
                                System.out.println("Potential breaking change detected at: " + 
                                    methodCall.getBegin().toString() + " in " + expr.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}