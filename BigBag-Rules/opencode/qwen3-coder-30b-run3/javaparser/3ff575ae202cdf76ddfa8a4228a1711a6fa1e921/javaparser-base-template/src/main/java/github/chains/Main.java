package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        // Find all Java files in the directory
        Set<Path> javaFiles = new HashSet<>();
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        // Process each Java file
        for (Path javaFile : javaFiles) {
            processFile(javaFile);
        }
    }
    
    private static void processFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Create a visitor to find and transform the problematic patterns
        new GetLoggerManagerVisitor().visit(cu, null);
        
        // Save the modified file
        try (PrintWriter out = new PrintWriter(new FileWriter(javaFile.toFile()))) {
            out.println(cu.toString());
        }
    }
    
    private static class GetLoggerManagerVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to getLoggerManager()
            if ("getLoggerManager".equals(methodCall.getNameAsString())) {
                // Check if this is part of a chain: container.getLoggerManager().setThreshold(...)
                Expression parent = methodCall.getParentNode().orElse(null);
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr parentCall = (MethodCallExpr) parent;
                    if ("setThreshold".equals(parentCall.getNameAsString())) {
                        // We have a pattern like: container.getLoggerManager().setThreshold(...)
                        // Transform it to: container.getLogger().setThreshold(...)
                        
                        // Get the receiver (container) of the getLoggerManager call
                        Expression receiver = methodCall.getScope().orElse(null);
                        if (receiver != null) {
                            // Create new expression: container.getLogger()
                            MethodCallExpr newGetLoggerCall = new MethodCallExpr(
                                receiver,
                                "getLogger"
                            );
                            
                            // Create new expression: container.getLogger().setThreshold(...)
                            MethodCallExpr newCall = new MethodCallExpr(
                                newGetLoggerCall,
                                parentCall.getNameAsString(),
                                parentCall.getArguments()
                            );
                            
                            // Replace the parent method call (setThreshold) 
                            parentCall.replace(newCall);
                        }
                    }
                }
            }
        }
    }
}