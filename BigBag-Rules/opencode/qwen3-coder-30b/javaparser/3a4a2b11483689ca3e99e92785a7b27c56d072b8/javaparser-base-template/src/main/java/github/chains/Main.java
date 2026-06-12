package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to fix breaking dependency changes in ScriptResult usage.
 * This rule addresses the issue where ScriptResult constructor was changed/removed 
 * in the acceptance-test-harness dependency.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        Path projectRoot = Paths.get("/workspace/code-coverage-api-plugin");
        processDirectory(projectRoot);
        System.out.println("Transformation completed successfully!");
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file content
            String content = Files.readString(filePath);
            
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Create a visitor to find and fix ScriptResult usage
            ScriptResultFixVisitor visitor = new ScriptResultFixVisitor();
            visitor.visit(cu, null);
            
            // Write back the modified content
            String modifiedContent = cu.toString();
            if (!content.equals(modifiedContent)) {
                Files.writeString(filePath, modifiedContent);
                System.out.println("Fixed file: " + filePath);
            }
        } catch (Exception e) {
            // Don't fail on individual file errors, continue with others
            // System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes ScriptResult usage in method calls
     */
    private static class ScriptResultFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check for the specific pattern: new ScriptResult(result).getJavaScriptResult()
            if (isScriptResultGetJavaScriptResult(methodCall)) {
                replaceScriptResultGetJavaScriptResult(methodCall);
            }
        }
        
        private boolean isScriptResultGetJavaScriptResult(MethodCallExpr methodCall) {
            // Check if it's a call to getJavaScriptResult()
            if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the scope is a ScriptResult constructor call
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                        if (objectCreation.getType().getNameAsString().equals("ScriptResult")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        
        private void replaceScriptResultGetJavaScriptResult(MethodCallExpr methodCall) {
            // Get the ScriptResult constructor call
            if (methodCall.getScope().isPresent() && methodCall.getScope().get() instanceof ObjectCreationExpr) {
                ObjectCreationExpr scriptResultCall = (ObjectCreationExpr) methodCall.getScope().get();
                
                // Get the argument to ScriptResult constructor (the result)
                if (scriptResultCall.getArguments().size() > 0) {
                    Expression resultArgument = scriptResultCall.getArguments().get(0);
                    
                    // Replace the entire expression with just the result argument
                    // From: new ScriptResult(result).getJavaScriptResult()
                    // To: result
                    if (methodCall.getParentNode().isPresent()) {
                        try {
                            // Replace the parent expression
                            if (methodCall.getParentNode().get() instanceof ExpressionStmt) {
                                ExpressionStmt exprStmt = (ExpressionStmt) methodCall.getParentNode().get();
                                exprStmt.setExpression(resultArgument);
                            } else {
                                methodCall.replace(resultArgument);
                            }
                        } catch (Exception e) {
                            methodCall.replace(resultArgument);
                        }
                    }
                }
            }
        }
    }
}