package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation to fix jcabi-aspects breaking change:
 * Replaces com.jcabi.aspects.Tv constants with literal values.
 * 
 * Specifically replaces:
 * - Tv.FIVE → 5
 * - Tv.HUNDRED → 100
 * - Tv.THOUSAND → 1000
 * - Tv.HUNDRED * Tv.THOUSAND → 100 * 1000
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
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files recursively
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Create visitor to transform Tv constants
            TvConstantVisitor visitor = new TvConstantVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString(); // Force toString to trigger serialization
            Files.write(filePath, cu.toString().getBytes());
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces Tv constants with literal values
     */
    private static class TvConstantVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing a Tv constant
            if (n.getScope().toString().equals("Tv")) {
                String fieldName = n.getNameAsString();
                
                switch (fieldName) {
                    case "FIVE":
                        return new IntegerLiteralExpr("5");
                    case "HUNDRED":
                        return new IntegerLiteralExpr("100");
                    case "THOUSAND":
                        return new IntegerLiteralExpr("1000");
                    default:
                        // Not a constant we're interested in
                        return super.visit(n, arg);
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(BinaryExpr n, Void arg) {
            // Check if this is a multiplication of two Tv constants
            if (n.getOperator() == BinaryExpr.Operator.MULTIPLY) {
                Expression left = n.getLeft();
                Expression right = n.getRight();
                
                if (left instanceof FieldAccessExpr && 
                    ((FieldAccessExpr) left).getScope().toString().equals("Tv") &&
                    right instanceof FieldAccessExpr && 
                    ((FieldAccessExpr) right).getScope().toString().equals("Tv")) {
                    
                    String leftName = ((FieldAccessExpr) left).getNameAsString();
                    String rightName = ((FieldAccessExpr) right).getNameAsString();
                    
                    // Check for combinations we know about
                    if ((leftName.equals("HUNDRED") && rightName.equals("THOUSAND")) ||
                        (leftName.equals("THOUSAND") && rightName.equals("HUNDRED"))) {
                        
                        // Replace with 100 * 1000
                        return new BinaryExpr(
                            new IntegerLiteralExpr("100"),
                            new IntegerLiteralExpr("1000"),
                            BinaryExpr.Operator.MULTIPLY
                        );
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}