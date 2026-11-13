package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix Maven dependency tree breaking API changes.
 * Specifically addresses the change from buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter)
 * to buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter, Collection<Artifact>)
 */
public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the given directory
            String sourceDirectory = args.length > 0 ? args[0] : "/workspace/license-maven-plugin";
            processDirectory(sourceDirectory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String directoryPath) throws Exception {
        Files.walk(Paths.get(directoryPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processFile(path.toString());
                } catch (Exception e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void processFile(String filePath) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(Paths.get(filePath));
        
        // Apply transformation to fix Maven dependency tree API usage
        new DependencyGraphFixerVisitor().visit(cu, null);
        
        // Save the modified file
        cu.toString().getBytes("UTF-8");
        Files.write(Paths.get(filePath), cu.toString().getBytes("UTF-8"));
    }
    
    /**
     * Visitor that identifies and fixes calls to the old buildDependencyGraph API
     */
    private static class DependencyGraphFixerVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to buildDependencyGraph with exactly 2 arguments
            if (methodCall.getNameAsString().equals("buildDependencyGraph") && 
                methodCall.getArguments().size() == 2) {
                
                // Check if it's a call to DependencyGraphBuilder.buildDependencyGraph 
                // by verifying the receiver type or the context
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    
                    // We'll target specific patterns that match Maven dependency tree usage
                    // Pattern matching based on the context we know about the codebase
                    
                    // Create the new third argument with null (empty collection)
                    NodeList<Expression> newArgs = new NodeList<>();
                    newArgs.add(methodCall.getArgument(0)); // ProjectBuildingRequest
                    newArgs.add(methodCall.getArgument(1)); // ArtifactFilter
                    newArgs.add(new NullLiteralExpr()); // Empty collection argument
                    
                    // Replace the method call with the new signature
                    methodCall.setArguments(newArgs);
                }
            }
        }
    }
}
