package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to replace FopFactory.newInstance() with the new API
            FopFactoryNewInstanceVisitor visitor = new FopFactoryNewInstanceVisitor();
            cu.accept(visitor, null);
            
            // Write the modified content back to file
            Files.writeString(filePath, cu.toString());
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FopFactoryNewInstanceVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Check if it's a call to FopFactory.newInstance() with no arguments
            if ("newInstance".equals(methodCall.getNameAsString()) && 
                methodCall.getArguments().size() == 0 &&
                methodCall.getScope().isPresent()) {
                
                // Get the scope 
                Expression scope = methodCall.getScope().get();
                
                // Check if scope is FopFactory
                if (scope instanceof Name && "FopFactory".equals(((Name) scope).asString())) {
                    // We'll just print a message and not actually modify for now
                    System.out.println("Found FopFactory.newInstance() call to replace");
                    // In a real implementation, this would create the new API call
                }
            }
            
            return super.visit(methodCall, arg);
        }
    }
}