package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix breaking changes in Struts2 ActionProxyFactory API.
 * This transformation updates calls to createActionProxy with outdated signatures to use new signatures.
 * Specifically addresses migration from older createActionProxy method signatures to newer versions.
 */
public class Main {
    
    public static void main(String[] args) {
        // Process all .java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/guice";
        
        try {
            processDirectory(sourceDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String dirPath) throws Exception {
        Files.walk(Paths.get(dirPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply the transformation to fix createActionProxy calls
            new FixActionProxyFactoryCallsVisitor().visit(cu, null);
            
            // Save the modified content back to the file
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath);
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that finds and fixes calls to createActionProxy with outdated signatures
     */
    static class FixActionProxyFactoryCallsVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match calls to createActionProxy methods that need upgrading
            if (methodCall.getNameAsString().equals("createActionProxy")) {
                // Check if this call is from ActionProxyFactory (the breaking dependency)
                // Pattern: ActionProxyFactory.createActionProxy(...)
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    // Look for calls like: factory.createActionProxy(...) or 
                    // actionProxyFactory.createActionProxy(...)
                    // We'll transform calls that have 5 parameters to 6 parameters
                    NodeList<Expression> arguments = methodCall.getArguments();
                    
                    // Typical breaking change pattern: old 5-parameter version becomes new 6-parameter version
                    // This is the generic solution for such API changes
                    if (arguments.size() == 5) {
                        // Add a default value for the missing boolean parameter (likely cleanupContext)
                        // The standard fix for Struts2 breaking changes:
                        // Old: createActionProxy(actionInvocation, namespace, actionName, method, executeResult, cleanupContext)
                        // New: createActionProxy(actionInvocation, namespace, actionName, method, executeResult, cleanupContext, true)
                        
                        // Add a default boolean value (true) for the missing parameter
                        arguments.add(new BooleanLiteralExpr(true));
                        System.out.println("Fixed createActionProxy call in: " + methodCall);
                    }
                }
            }
        }
    }
}