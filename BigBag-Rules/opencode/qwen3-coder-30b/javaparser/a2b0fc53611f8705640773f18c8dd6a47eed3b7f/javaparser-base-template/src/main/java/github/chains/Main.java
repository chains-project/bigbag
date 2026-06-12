package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // Define the source directory to process
        String sourceDir = "/workspace/nem";
        
        // Get all Java files in the source directory
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(java.util.stream.Collectors.toList());
        
        // Process each Java file
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Find all method declarations that contain Flyway creation with method calls
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            
            for (MethodDeclaration method : methods) {
                // Get the method body
                BlockStmt body = method.getBody().orElse(null);
                if (body == null) continue;
                
                // Look for return statements with Flyway creation
                List<ReturnStmt> returnStmts = body.findAll(ReturnStmt.class);
                for (ReturnStmt returnStmt : returnStmts) {
                    if (!returnStmt.getExpression().isPresent()) continue;
                    
                    // Check if the return expression is a new Flyway constructor
                    if (returnStmt.getExpression().get() instanceof ObjectCreationExpr oce && 
                        oce.getType().getNameAsString().equals("Flyway") && oce.getArguments().isEmpty()) {
                        
                        // Find all method calls in this method body
                        List<MethodCallExpr> methodCalls = body.findAll(MethodCallExpr.class);
                        String newConfig = "Flyway.configure()";
                        boolean hasMethodCalls = false;
                        
                        // Collect all method calls that are on flyway variable
                        for (MethodCallExpr call : methodCalls) {
                            if (call.getScope().isPresent() && 
                                call.getScope().get().toString().equals("flyway")) {
                                
                                String methodName = call.getNameAsString();
                                String argString = call.getArguments().get(0).toString();
                                
                                // Map old method names to new fluent API
                                if (methodName.equals("setDataSource")) {
                                    newConfig += ".dataSource(" + argString + ")";
                                    hasMethodCalls = true;
                                } else if (methodName.equals("setLocations")) {
                                    newConfig += ".locations(" + argString + ")";
                                    hasMethodCalls = true;
                                } else if (methodName.equals("setValidateOnMigrate")) {
                                    newConfig += ".validateOnMigrate(" + argString + ")";
                                    hasMethodCalls = true;
                                } else if (methodName.equals("setClassLoader")) {
                                    newConfig += ".classLoader(" + argString + ")";
                                    hasMethodCalls = true;
                                }
                                
                                // Remove the old method call
                                call.remove();
                            }
                        }
                        
                        // If we have method calls to convert, replace the entire pattern
                        if (hasMethodCalls) {
                            // Replace the return statement with the new fluent configuration
                            returnStmt.setExpression(StaticJavaParser.parseExpression(newConfig));
                        }
                    }
                }
            }
            
            // Write the modified file back
            Files.write(javaFile, cu.toString().getBytes());
        }
        
        System.out.println("Transformation complete!");
    }
}