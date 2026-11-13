package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for Hamcrest breaking changes.
 * Fixes StringContains and StringStartsWith constructor calls that changed from
 * (boolean, String) to (String) parameters.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
        
        System.out.println("Hamcrest transformation completed successfully!");
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            HamcrestFixVisitor visitor = new HamcrestFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString(); // Forces an update
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes Hamcrest constructor calls.
     */
    private static class HamcrestFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is a StringContains or StringStartsWith constructor call
            String typeName = expr.getType().asString();
            if ("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) {
                
                // Check if it has 2 arguments (boolean, String)
                if (expr.getArguments().size() == 2) {
                    Expression firstArg = expr.getArguments().get(0);
                    Expression secondArg = expr.getArguments().get(1);
                    
                    // Check if first argument is a boolean literal
                    if (firstArg instanceof BooleanLiteralExpr) {
                        // Replace with single argument constructor call
                        expr.setArguments(com.github.javaparser.ast.NodeList.nodeList(secondArg));
                    }
                }
            }
            return super.visit(expr, arg);
        }
    }
}