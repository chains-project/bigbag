package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Generic JavaParser transformation to replace Tv constants with numeric values.
 * This fixes breaking changes in jcabi-aspects where Tv class was removed.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
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
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create a visitor to replace Tv constants
            TvConstantReplacer visitor = new TvConstantReplacer();
            cu.accept(visitor, null);
            
            // Remove Tv import if present
            cu.getImports().removeIf(importDecl -> {
                // Check if this is an import of com.jcabi.aspects.Tv
                if (importDecl.isImportDeclaration()) {
                    String importedName = importDecl.getName().toString();
                    return importedName.equals("com.jcabi.aspects.Tv");
                }
                return false;
            });
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces Tv constants with numeric values
     */
    private static class TvConstantReplacer extends ModifierVisitor<Void> {
        private static final List<String> TV_CONSTANTS = Arrays.asList(
            "TEN", "EIGHT", "FIVE", "FOUR", "THREE", "TWO", "ONE", "ZERO"
        );
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a Tv constant access
            if (n.getScope() instanceof com.github.javaparser.ast.expr.NameExpr) {
                com.github.javaparser.ast.expr.NameExpr scope = (com.github.javaparser.ast.expr.NameExpr) n.getScope();
                if (scope.getName().getIdentifier().equals("Tv")) {
                    String fieldName = n.getName().getIdentifier();
                    
                    // Replace with appropriate numeric value
                    Integer replacementValue = getNumericValue(fieldName);
                    if (replacementValue != null) {
                        return new IntegerLiteralExpr(replacementValue.toString());
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private Integer getNumericValue(String constantName) {
            switch (constantName) {
                case "TEN": return 10;
                case "EIGHT": return 8;
                case "FIVE": return 5;
                case "FOUR": return 4;
                case "THREE": return 3;
                case "TWO": return 2;
                case "ONE": return 1;
                case "ZERO": return 0;
                default: return null;
            }
        }
    }
}
