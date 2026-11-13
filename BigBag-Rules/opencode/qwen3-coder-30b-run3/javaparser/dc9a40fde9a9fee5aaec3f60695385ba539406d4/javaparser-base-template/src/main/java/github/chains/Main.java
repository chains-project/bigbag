/*
 * Generic transformation for fixing Dubbo AsyncRpcResult compatibility issue.
 * 
 * Problem: In Dubbo 2.7.21+, the return type of AsyncRpcResult.setFuture() changed
 * from AsyncRpcResult to CompletableFuture, causing compilation errors when
 * attempting to pass AsyncRpcResult objects directly to RpcContext.getContext().setFuture().
 * 
 * Solution: Transform calls to RpcContext.getContext().setFuture(asyncRpcResult) to
 * RpcContext.getContext().setFuture(asyncRpcResult.getResponseFuture())
 * 
 * This rule can be applied to any project that has the same pattern in its codebase.
 */

package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-file-path>");
            System.exit(1);
        }
        
        String sourceFilePath = args[0];
        try {
            // Parse the source file
            FileInputStream inputStream = new FileInputStream(sourceFilePath);
            CompilationUnit cu = StaticJavaParser.parse(inputStream);
            
            // Apply the transformation
            new DubboFutureFixTransformer().visit(cu, null);
            
            // Write back to file
            Path outputPath = Paths.get(sourceFilePath);
            Files.write(outputPath, cu.toString().getBytes());
            
            System.out.println("Transformation complete: " + sourceFilePath);
            
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Transformer that fixes the Dubbo AsyncRpcResult compatibility issue
     * by converting direct AsyncRpcResult references to use getResponseFuture()
     */
    static class DubboFutureFixTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            // Look for RpcContext.getContext().setFuture calls
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                
                // Match the specific pattern: RpcContext.getContext().setFuture(...)
                if (scope.toString().equals("RpcContext.getContext().setFuture")) {
                    // Check if there's exactly one argument
                    if (methodCall.getArguments().size() == 1) {
                        Expression argument = methodCall.getArguments().get(0);
                        
                        // If the argument is a simple variable name that likely refers to AsyncRpcResult
                        if (argument instanceof NameExpr) {
                            NameExpr nameExpr = (NameExpr) argument;
                            String varName = nameExpr.getNameAsString();
                            
                            // Check if it's likely an AsyncRpcResult variable
                            if (varName.contains("asyncRpcResult") || 
                                varName.contains("AsyncRpcResult")) {
                                // Convert to: varName.getResponseFuture()
                                return new MethodCallExpr(
                                    new NameExpr(varName), 
                                    "getResponseFuture"
                                );
                            }
                        }
                    }
                }
                
                // Also handle FutureContext.getContext().setCompatibleFuture calls
                if (scope.toString().equals("FutureContext.getContext().setCompatibleFuture")) {
                    // Check if there's exactly one argument
                    if (methodCall.getArguments().size() == 1) {
                        Expression argument = methodCall.getArguments().get(0);
                        
                        // If the argument is a simple variable name that likely refers to AsyncRpcResult
                        if (argument instanceof NameExpr) {
                            NameExpr nameExpr = (NameExpr) argument;
                            String varName = nameExpr.getNameAsString();
                            
                            // Check if it's likely an AsyncRpcResult variable
                            if (varName.contains("asyncRpcResult") || 
                                varName.contains("AsyncRpcResult")) {
                                // Convert to: varName.getResponseFuture()
                                return new MethodCallExpr(
                                    new NameExpr(varName), 
                                    "getResponseFuture"
                                );
                            }
                        }
                    }
                }
            }
            
            return methodCall;
        }
    }
}