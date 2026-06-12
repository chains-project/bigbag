package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * A generic JavaParser transformation that fixes the Tv class removal in jcabi-aspects.
 * 
 * This transformation replaces references to Tv.FIVE, Tv.HUNDRED, and Tv.THOUSAND
 * with their numeric values (5, 100, 1000 respectively).
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path directory = Paths.get(sourceDir);
        
        if (!Files.exists(directory)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation
            TvFixerVisitor visitor = new TvFixerVisitor();
            cu.accept(visitor, null);
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces Tv.FIVE, Tv.HUNDRED, and Tv.THOUSAND with numeric values
     */
    private static class TvFixerVisitor extends ModifierVisitor<Void> {
        
        private static final List<String> TV_CONSTANTS = Arrays.asList("FIVE", "HUNDRED", "THOUSAND");
        
        @Override
        public Visitable visit(FieldAccessExpr fieldAccess, Void arg) {
            // Check if this is accessing Tv.FIVE, Tv.HUNDRED, or Tv.THOUSAND
            if (fieldAccess.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) fieldAccess.getScope();
                if ("Tv".equals(scope.getNameAsString()) && 
                    TV_CONSTANTS.contains(fieldAccess.getNameAsString())) {
                    
                    // Create the appropriate numeric literal
                    int value = getNumericValue(fieldAccess.getNameAsString());
                    return new IntegerLiteralExpr(String.valueOf(value));
                }
            }
            return super.visit(fieldAccess, arg);
        }
        
        private int getNumericValue(String constantName) {
            switch (constantName) {
                case "FIVE": return 5;
                case "HUNDRED": return 100;
                case "THOUSAND": return 1000;
                default: return 0;
            }
        }
    }
}