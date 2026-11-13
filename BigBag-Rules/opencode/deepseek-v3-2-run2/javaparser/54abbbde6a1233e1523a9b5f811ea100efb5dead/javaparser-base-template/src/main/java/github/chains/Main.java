package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation rule for fixing breaking changes when the Tv class
 * is removed from jcabi-aspects dependency.
 * 
 * This transformation:
 * 1. Removes imports of com.jcabi.aspects.Tv
 * 2. Replaces field access expressions like Tv.CONSTANT with literal integer values
 * 3. Is parameterized by the fully-qualified type name and field mappings
 */
public class Main {
    
    // Parameterize by the fully-qualified type name that was removed
    private static final String REMOVED_TYPE_NAME = "com.jcabi.aspects.Tv";
    
    // Parameterize by field name to integer value mappings
    private static final Map<String, Integer> FIELD_MAPPINGS = new HashMap<>();
    static {
        FIELD_MAPPINGS.put("SEVEN", 7);
        FIELD_MAPPINGS.put("TEN", 10);
        FIELD_MAPPINGS.put("MILLION", 1000000);
        // Add more mappings as needed for other projects
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation will fix breaking changes from removed Tv class");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse " + filePath));
            
            boolean modified = applyTransformation(cu);
            
            if (modified) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean applyTransformation(CompilationUnit cu) {
        TvRemovalVisitor visitor = new TvRemovalVisitor();
        cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    /**
     * Visitor that removes Tv imports and replaces field accesses with literals
     */
    private static class TvRemovalVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove import of the removed Tv class
            if (importDecl.getNameAsString().equals(REMOVED_TYPE_NAME)) {
                modified = true;
                return null; // Remove this import
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr fieldAccess, Void arg) {
            // Check if this is a field access on the removed Tv class
            if (fieldAccess.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) fieldAccess.getScope();
                String fieldName = fieldAccess.getNameAsString();
                
                // Check if this matches the pattern: Tv.CONSTANT
                if (scope.getNameAsString().equals("Tv") && FIELD_MAPPINGS.containsKey(fieldName)) {
                    // Replace with literal integer value
                    Integer value = FIELD_MAPPINGS.get(fieldName);
                    modified = true;
                    return new IntegerLiteralExpr(value.toString());
                }
            }
            
            return (Node) super.visit(fieldAccess, arg);
        }
    }
}