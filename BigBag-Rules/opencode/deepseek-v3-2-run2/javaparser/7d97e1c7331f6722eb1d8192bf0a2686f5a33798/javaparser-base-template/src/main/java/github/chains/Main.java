package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation rule for replacing removed Tv class constants
 * with literal integer values.
 * 
 * This rule handles the breaking change where com.jcabi.aspects.Tv class
 * was removed from jcabi-aspects 0.25.1. It replaces field access expressions
 * like Tv.CONSTANT_NAME with corresponding integer literals.
 */
public class Main {
    
    // Mapping of Tv constant names to their integer values
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    static {
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("FIFTY", 50);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation replaces com.jcabi.aspects.Tv constants with integer literals");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(filePath);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                
                // Apply the transformation
                TvConstantReplacer replacer = new TvConstantReplacer();
                cu.accept(replacer, null);
                
                // Write the modified file back
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Processed: " + filePath);
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces Tv.CONSTANT field accesses with integer literals
     * and removes Tv imports.
     */
    private static class TvConstantReplacer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove imports of com.jcabi.aspects.Tv
            if (importDecl.getNameAsString().equals("com.jcabi.aspects.Tv")) {
                return null; // Remove this import
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr fieldAccess, Void arg) {
            // Check if this is a Tv.CONSTANT pattern
            if (fieldAccess.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) fieldAccess.getScope();
                if (scope.getNameAsString().equals("Tv")) {
                    String constantName = fieldAccess.getNameAsString();
                    Integer replacementValue = TV_CONSTANTS.get(constantName);
                    
                    if (replacementValue != null) {
                        System.out.println("Replacing Tv." + constantName + " with " + replacementValue);
                        return new IntegerLiteralExpr(replacementValue.toString());
                    } else {
                        System.err.println("Warning: Unknown Tv constant: " + constantName);
                    }
                }
            }
            
            return (Node) super.visit(fieldAccess, arg);
        }
    }
}