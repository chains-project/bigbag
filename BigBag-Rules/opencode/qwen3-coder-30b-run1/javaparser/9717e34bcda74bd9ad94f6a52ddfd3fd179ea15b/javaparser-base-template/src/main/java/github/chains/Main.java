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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation to replace Tv class constants with literal values.
 * This addresses the breaking change where com.jcabi.aspects.Tv was removed in 0.25.1
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                // Parse the Java file
                CompilationUnit cu = StaticJavaParser.parse(file);
                
                // Apply transformation
                TvConstantReplacerVisitor visitor = new TvConstantReplacerVisitor();
                cu.accept(visitor, null);
                
                // Save the modified file
                Files.write(file, cu.toString().getBytes());
            }
        }
    }
    
    /**
     * Visitor that replaces Tv constants with literal values
     */
    private static class TvConstantReplacerVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing a Tv constant
            if (n.getScope().isNameExpr() && 
                n.getScope().asNameExpr().getNameAsString().equals("Tv")) {
                
                String constantName = n.getNameAsString();
                Integer literalValue = getLiteralValue(constantName);
                
                if (literalValue != null) {
                    // Replace with integer literal
                    return new IntegerLiteralExpr(literalValue.toString());
                }
            }
            return super.visit(n, arg);
        }
        
        private Integer getLiteralValue(String constantName) {
            switch (constantName) {
                case "TEN": return 10;
                case "TWENTY": return 20;
                case "THIRTY": return 30;
                case "HUNDRED": return 100;
                case "BILLION": return 1000000000;
                case "THREE": return 3;
                case "FOUR": return 4;
                case "FIVE": return 5;
                case "SIX": return 6;
                case "THOUSAND": return 1000;
                case "FIFTEEN": return 15;
                default: return null;
            }
        }
    }
}