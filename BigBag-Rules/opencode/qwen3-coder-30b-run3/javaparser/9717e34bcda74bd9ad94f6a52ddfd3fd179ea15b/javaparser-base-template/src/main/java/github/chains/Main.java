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
 * Generic transformation rule to fix breaking changes in jcabi-aspects Tv class usage.
 * This rule replaces references to com.jcabi.aspects.Tv constants with their literal values.
 */
public class Main {
    
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation
            TransformationVisitor visitor = new TransformationVisitor();
            cu.accept(visitor, null);
            
            // Overwrite the file with the modified content
            Files.write(filePath, cu.toString().getBytes("UTF-8"));
            
        } catch (IOException e) {
            System.err.println("Failed to process file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms Tv constants to their literal values
     */
    private static class TransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Replace Tv constants with literal integers
            switch (n.toString()) {
                case "Tv.TWENTY": return new IntegerLiteralExpr("20");
                case "Tv.FIVE": return new IntegerLiteralExpr("5");
                case "Tv.TEN": return new IntegerLiteralExpr("10");
                case "Tv.THIRTY": return new IntegerLiteralExpr("30");
                case "Tv.HUNDRED": return new IntegerLiteralExpr("100");
                case "Tv.THREE": return new IntegerLiteralExpr("3");
                case "Tv.FOUR": return new IntegerLiteralExpr("4");
                case "Tv.SIX": return new IntegerLiteralExpr("6");
                case "Tv.BILLION": return new IntegerLiteralExpr("1000000000");
                case "Tv.THOUSAND": return new IntegerLiteralExpr("1000");
                case "Tv.FIFTEEN": return new IntegerLiteralExpr("15");
                default: return super.visit(n, arg);
            }
        }
    }
}
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation
            TransformationVisitor visitor = new TransformationVisitor();
            cu.accept(visitor, null);
            
            // Overwrite the file with the modified content
            Files.write(filePath, cu.toString().getBytes("UTF-8"));
            
        } catch (IOException e) {
            System.err.println("Failed to process file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms Tv constants to their literal values
     */
    private static class TransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Replace Tv.TWENTY with 20
            if (n.toString().equals("Tv.TWENTY")) {
                return new IntegerLiteralExpr("20");
            }
            // Replace Tv.FIVE with 5
            else if (n.toString().equals("Tv.FIVE")) {
                return new IntegerLiteralExpr("5");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove imports of Tv class
            if (n.getNameAsString().equals("com.jcabi.aspects.Tv")) {
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
    }
}
