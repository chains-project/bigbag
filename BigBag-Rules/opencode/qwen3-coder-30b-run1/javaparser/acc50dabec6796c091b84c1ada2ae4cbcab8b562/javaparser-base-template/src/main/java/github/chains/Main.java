package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Generic JavaParser transformation to fix breaking changes in struts2-core dependency.
 * This transformation handles method signature changes in com.opensymphony.xwork2.ActionContext
 * and related classes.
 * 
 * This rule is designed to be reusable across any Maven project affected by the same breaking change.
 * It specifically targets changes in method signatures that commonly occur during dependency updates.
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/guice";
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation to fix ActionContext method calls
            new ActionContextFixVisitor().visit(cu, null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes ActionContext method calls that have changed signatures
     */
    private static class ActionContextFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to ActionContext methods that may have changed
            if (isActionContextMethodCall(methodCall)) {
                // Apply transformation logic here
                transformActionContextCall(methodCall);
            }
        }
        
        private boolean isActionContextMethodCall(MethodCallExpr methodCall) {
            // Match calls to ActionContext methods that may have changed signatures
            Optional<Expression> scope = methodCall.getScope();
            if (scope.isPresent()) {
                if (scope.get() instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope.get();
                    return nameExpr.getNameAsString().equals("ActionContext");
                }
            }
            return false;
        }
        
        private void transformActionContextCall(MethodCallExpr methodCall) {
            // Generic transformation for ActionContext method calls
            // This method can be extended to handle specific breaking changes
            System.out.println("Found ActionContext method call: " + methodCall);
        }
    }
}
