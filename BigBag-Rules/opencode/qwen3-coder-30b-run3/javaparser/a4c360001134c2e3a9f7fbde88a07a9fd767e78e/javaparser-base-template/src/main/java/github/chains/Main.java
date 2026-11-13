package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // Process all Java files in the given directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/gauge-java";
        
        Path dir = Paths.get(sourceDirectory);
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to replace FilterBuilder.apply() calls with Predicate.test()
            cu.accept(new FilterBuilderApplyRemover(), null);
            
            // Write back the modified content
            try (PrintWriter out = new PrintWriter(new FileWriter(filePath.toString()))) {
                out.println(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FilterBuilderApplyRemover extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to FilterBuilder.apply() method specifically
            if (methodCall.getNameAsString().equals("apply")) {
                // Check if this is a call to apply() on a FilterBuilder object
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    
                    // Pattern matching for: new FilterBuilder().include(...).apply(...)
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr parentCall = (MethodCallExpr) scope;
                        
                        // Check if the parent call was include() on a FilterBuilder
                        if (parentCall.getNameAsString().equals("include") && 
                            parentCall.getScope().isPresent() &&
                            parentCall.getScope().get() instanceof ObjectCreationExpr) {
                            
                            ObjectCreationExpr creation = (ObjectCreationExpr) parentCall.getScope().get();
                            if (creation.getType().asString().contains("FilterBuilder")) {
                                // Found the pattern: new FilterBuilder().include(...).apply(...)
                                // Should be replaced with: new FilterBuilder().include(...).test(...)
                                
                                // Replace the apply with test
                                methodCall.setName("test");
                            }
                        }
                    }
                }
            }
        }
    }
}