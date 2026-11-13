package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/bval";
        
        System.out.println("Processing Java files in: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        // Process each Java file
        int successCount = 0;
        for (Path javaFile : javaFiles) {
            if (processJavaFile(javaFile.toFile())) {
                successCount++;
            }
        }
        
        System.out.println("Successfully processed " + successCount + " Java files");
    }
    
    private static boolean processJavaFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply transformation to fix Mockito getArgumentAt calls
            MockitoFixVisitor visitor = new MockitoFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(file)) {
                cu.print(writer);
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error processing " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Visitor that fixes the deprecated Mockito getArgumentAt calls
     */
    private static class MockitoFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Check if this is a call to getArgumentAt with two parameters
            if (methodCall.getNameAsString().equals("getArgumentAt") &&
                methodCall.getArguments().size() == 2) {
                
                // Create the inner getArgument call
                MethodCallExpr getArgumentCall = new MethodCallExpr(
                    methodCall.getScope().orElse(null),
                    "getArgument",
                    methodCall.getArguments().get(0)
                );
                
                // Create the cast call
                MethodCallExpr newMethodCall = new MethodCallExpr(
                    getArgumentCall,
                    "cast",
                    methodCall.getArguments().get(1)
                );
                
                return newMethodCall;
            }
            return super.visit(methodCall, arg);
        }
    }
}
