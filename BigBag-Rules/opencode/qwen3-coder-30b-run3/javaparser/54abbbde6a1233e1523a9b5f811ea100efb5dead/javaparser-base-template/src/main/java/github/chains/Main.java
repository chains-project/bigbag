package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule for replacing Tv constants with literal values.
 * This addresses the issue where com.jcabi.aspects.Tv class was removed in version 0.25.1.
 * 
 * The transformation replaces expressions like:
 *   Tv.SEVEN -> 7
 *   Tv.TEN -> 10  
 *   Tv.MILLION -> 1000000
 */
public class Main {
    
    public static void main(String[] args) throws Exception {
        // Process all Java files in the source directory
        String sourceDir = "/workspace/jcabi-ssh";
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        System.out.println("Processing Java files in: " + sourceDir);
        
        // Walk through all Java files in the directory
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply the transformation visitor
            TvReplacementVisitor visitor = new TvReplacementVisitor();
            cu.accept(visitor, null);
            
            // Write back the modified content
            Files.writeString(filePath, cu.toString());
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces Tv constants with literal values.
     */
    private static class TvReplacementVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing a Tv constant
            if (n.getScope().toString().equals("Tv")) {
                String fieldName = n.getNameAsString();
                
                // Replace with literal values
                if ("SEVEN".equals(fieldName)) {
                    return new IntegerLiteralExpr("7");
                } else if ("TEN".equals(fieldName)) {
                    return new IntegerLiteralExpr("10");
                } else if ("MILLION".equals(fieldName)) {
                    return new IntegerLiteralExpr("1000000");
                }
            }
            return super.visit(n, arg);
        }
    }
}