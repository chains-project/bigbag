package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix Maven dependency tree API breaking changes.
 * Specifically fixes calls to buildDependencyGraph(MavenProject, ArtifactFilter)
 * to buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter)
 */
public class Main {
    
    public static void main(String[] args) {
        // This transformation is designed to be generic and reusable
        // It looks for calls to buildDependencyGraph with MavenProject as first parameter
        // and replaces them with calls using ProjectBuildingRequest
        
        System.out.println("Running generic Maven dependency tree API fix...");
        
        try {
            // Process all Java files in the target directory
            Path sourceDir = Paths.get("/workspace/license-maven-plugin");
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation completed.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            boolean modified = false;
            
            // Look for method calls to buildDependencyGraph that use MavenProject as first argument
            for (MethodCallExpr methodCall : cu.findAll(MethodCallExpr.class)) {
                if ("buildDependencyGraph".equals(methodCall.getNameAsString()) && 
                    methodCall.getScope().isPresent() &&
                    methodCall.getArguments().size() >= 2) {
                    
                    // Check if the first argument is a variable that refers to a MavenProject
                    // and not already a ProjectBuildingRequest
                    Expression firstArg = methodCall.getArguments().get(0);
                    String firstArgStr = firstArg.toString();
                    
                    // If the first argument seems to be a MavenProject reference (not already a ProjectBuildingRequest)
                    if (!firstArgStr.contains("ProjectBuildingRequest") && 
                        !firstArgStr.contains("getBuildingRequest") &&
                        !firstArgStr.contains("buildingRequest")) {
                        
                        // Transform the call to use getBuildingRequest() instead of the MavenProject
                        NodeList<Expression> newArgs = new NodeList<>();
                        
                        // First argument: add getBuildingRequest() call
                        newArgs.add(new MethodCallExpr(new ThisExpr(), "getBuildingRequest"));
                        
                        // Second argument: keep the original artifact filter argument
                        newArgs.add(methodCall.getArguments().get(1));
                        
                        // Update the method call with new arguments
                        methodCall.setArguments(newArgs);
                        modified = true;
                    }
                }
            }
            
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            boolean modified = false;
            
            // Look for method calls to buildDependencyGraph that use MavenProject as first arg
            for (MethodCallExpr methodCall : cu.findAll(MethodCallExpr.class)) {
                if (isBuildDependencyGraphCall(methodCall)) {
                    // Check if the first argument is a MavenProject and not a ProjectBuildingRequest
                    if (isValidMavenProjectArg(methodCall)) {
                        // Transform the call
                        transformBuildDependencyGraphCall(methodCall);
                        modified = true;
                    }
                }
            }
            
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean isBuildDependencyGraphCall(MethodCallExpr methodCall) {
        // Check if it's a call to buildDependencyGraph method
        return methodCall.getNameAsString().equals("buildDependencyGraph") &&
               methodCall.getScope().isPresent() &&
               methodCall.getScope().get() instanceof FieldAccessExpr;
    }
    
    private static boolean isValidMavenProjectArg(MethodCallExpr methodCall) {
        // Check if first argument is MavenProject (instead of ProjectBuildingRequest)
        NodeList<Expression> arguments = methodCall.getArguments();
        if (arguments.size() >= 2) {
            Expression firstArg = arguments.get(0);
            // Check if it's a MavenProject reference - this is a simplified check
            // In a real implementation, we'd check the type more precisely
            return !firstArg.toString().contains("ProjectBuildingRequest");
        }
        return false;
    }
    
    private static void transformBuildDependencyGraphCall(MethodCallExpr methodCall) {
        // Replace the method call to use ProjectBuildingRequest instead of MavenProject
        // This is a simplified approach, in reality we would want to:
        // 1. Find the appropriate ProjectBuildingRequest to use
        // 2. Change the method signature appropriately
        
        // Create new method call that uses getBuildingRequest()
        NodeList<Expression> newArgs = new NodeList<>();
        
        // First argument becomes getBuildingRequest()
        newArgs.add(new MethodCallExpr(new ThisExpr(), "getBuildingRequest"));
        
        // Second argument remains the same
        if (methodCall.getArguments().size() > 1) {
            newArgs.add(methodCall.getArguments().get(1));
        }
        
        // Update the method call with new arguments
        methodCall.setArguments(newArgs);
    }
}
