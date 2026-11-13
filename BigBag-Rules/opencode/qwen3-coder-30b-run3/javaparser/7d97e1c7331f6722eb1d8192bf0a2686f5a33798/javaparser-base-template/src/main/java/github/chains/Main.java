package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Generic JavaParser transformation to replace Tv.TEN and Tv.EIGHT constants
 * with their literal values (10 and 8 respectively) to fix breaking changes
 * in jcabi-aspects dependency.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jcabi-simpledb/src";
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation
            TransformVisitor visitor = new TransformVisitor();
            cu.accept(visitor, null);
            
            // Write back to file
            Files.writeString(filePath, cu.toString());
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms Tv.TEN and Tv.EIGHT references to literal values
     */
    private static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing Tv.TEN or Tv.EIGHT
            if (n.getScope().isNameExpr()) {
                if ("Tv".equals(n.getScope().asNameExpr().getName().asString())) {
                    if ("TEN".equals(n.getName().asString())) {
                        // Replace Tv.TEN with 10
                        return new IntegerLiteralExpr("10");
                    } else if ("EIGHT".equals(n.getName().asString())) {
                        // Replace Tv.EIGHT with 8
                        return new IntegerLiteralExpr("8");
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove import statements for Tv
            if (n.getNameAsString().equals("com.jcabi.aspects.Tv")) {
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
    }
}
