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

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            new ScriptResultFixVisitor().visit(cu, null);
            String content = cu.toString();
            Files.write(filePath, content.getBytes());
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds and fixes ScriptResult usage patterns:
     * - new ScriptResult(result) constructor calls
     * - scriptResult.getJavaScriptResult() method calls
     */
    private static class ScriptResultFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            // Check for new ScriptResult(...) constructor calls
            if (stmt.getExpression() instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) stmt.getExpression();
                // Check if it's creating a ScriptResult object
                if (creation.getType().toString().contains("ScriptResult")) {
                    // Replace new ScriptResult(result) with just result
                    if (creation.getArguments().size() > 0) {
                        // Get first argument and replace the entire statement with it
                        stmt.replace(creation.getArguments().get(0));
                    }
                }
            }
            
            // Check for method calls like scriptResult.getJavaScriptResult()
            if (stmt.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) stmt.getExpression();
                if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                    // Replace scriptResult.getJavaScriptResult() with just scriptResult
                    if (methodCall.getScope().isPresent()) {
                        stmt.replace(methodCall.getScope().get());
                    }
                }
            }
            
            super.visit(stmt, arg);
        }
    }
}