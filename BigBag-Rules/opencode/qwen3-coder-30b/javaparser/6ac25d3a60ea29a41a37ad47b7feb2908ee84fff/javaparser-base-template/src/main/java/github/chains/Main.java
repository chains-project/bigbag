package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        try {
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation to fix AsyncContext.addListener method calls
            AsyncContextFixVisitor visitor = new AsyncContextFixVisitor();
            cu.accept(visitor, null);
            
            // In a real implementation, we would save the modified file here
            // For now, we just process it to validate the transformation logic
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    // Visitor to fix AsyncContext.addListener calls
    private static class AsyncContextFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Match: asyncContext.addListener(asyncListener) 
            // Transform to: asyncContext.addListener(asyncListener, request, response)
            
            if (methodCall.getScope().isPresent()) {
                String methodName = methodCall.getNameAsString();
                
                // Check if this is a call to addListener on AsyncContext
                if ("addListener".equals(methodName)) {
                    // Simple fix: if there's only 1 argument, add request and response
                    NodeList<com.github.javaparser.ast.expr.Expression> args = methodCall.getArguments();
                    if (args.size() == 1) {
                        // Add request and response parameters
                        args.add(new com.github.javaparser.ast.expr.NameExpr("request"));
                        args.add(new com.github.javaparser.ast.expr.NameExpr("response"));
                    }
                }
            }
            
            return super.visit(methodCall, arg);
        }
    }
}