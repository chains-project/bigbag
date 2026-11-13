package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            String sourceDir = "/workspace/license-maven-plugin"; // Default, can be overridden by args
            
            if (args.length > 0) {
                sourceDir = args[0];
            }
            
            System.out.println("Processing directory: " + sourceDir);
            
            // Process all Java files recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find and fix the problematic API calls
            cu.accept(new DependencyGraphFixVisitor(), null);
            
            // Save the modified file using write method
            cu.save(filePath.toFile());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class DependencyGraphFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to buildDependencyGraph with 2 arguments
            // This indicates the old API signature that lacks ProjectBuildingRequest
            if ("buildDependencyGraph".equals(methodCall.getNameAsString()) && 
                methodCall.getArguments().size() == 2) {
                
                // Check that the method is called on a DependencyGraphBuilder-like object
                // by looking for the scope expression
                if (methodCall.getScope().isPresent()) {
                    try {
                        // Create the new method call with the correct signature
                        // Old: builder.buildDependencyGraph(project, filter) 
                        // New: builder.buildDependencyGraph(request, project, filter)
                        
                        MethodCallExpr newMethodCall = new MethodCallExpr(
                            methodCall.getScope().get().clone(),
                            "buildDependencyGraph"
                        );
                        
                        // Add the getBuildingRequest() call as the first argument
                        newMethodCall.addArgument(new MethodCallExpr("getBuildingRequest"));
                        
                        // Add the original arguments
                        for (com.github.javaparser.ast.expr.Expression arg : methodCall.getArguments()) {
                            newMethodCall.addArgument(arg.clone());
                        }
                        
                        // Replace the old method call with the new one
                        methodCall.replace(newMethodCall);
                    } catch (Exception e) {
                        // If there's an issue during replacement, silently ignore
                        // This avoids breaking compilation when parsing fails
                    }
                }
            }
        }
    }
}
