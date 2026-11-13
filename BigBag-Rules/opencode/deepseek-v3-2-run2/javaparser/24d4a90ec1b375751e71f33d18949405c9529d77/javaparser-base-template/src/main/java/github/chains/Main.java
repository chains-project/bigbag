package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {
    
    // Configuration: Map of old field names to their literal integer values
    // This makes the transformation generic - can be configured for any removed constants
    private static final Map<String, Integer> CONSTANT_MAPPINGS = new HashMap<>();
    static {
        CONSTANT_MAPPINGS.put("FIVE", 5);
        CONSTANT_MAPPINGS.put("HUNDRED", 100);
        CONSTANT_MAPPINGS.put("THOUSAND", 1000);
    }
    
    // Configuration: Fully qualified class name that was removed
    private static final String REMOVED_CLASS_NAME = "com.jcabi.aspects.Tv";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("This transformation replaces field access expressions of removed constants with literal values.");
            System.err.println("Configuration:");
            System.err.println("  - Removed class: " + REMOVED_CLASS_NAME);
            System.err.println("  - Constant mappings: " + CONSTANT_MAPPINGS);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Replace " + REMOVED_CLASS_NAME + " constants with literal values");
        
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
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = cu.accept(new ConstantReplacementVisitor(), null) != null;
            
            if (modified) {
                System.out.println("Modified: " + filePath);
                Files.write(filePath, cu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ConstantReplacementVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a field access expression matching our pattern
            if (isTargetFieldAccess(n)) {
                String fieldName = n.getNameAsString();
                Integer replacementValue = CONSTANT_MAPPINGS.get(fieldName);
                
                if (replacementValue != null) {
                    System.out.println("  Replacing " + REMOVED_CLASS_NAME + "." + fieldName + " with " + replacementValue);
                    return new IntegerLiteralExpr(replacementValue.toString());
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Remove import statements for the removed class
            if (n.getNameAsString().equals(REMOVED_CLASS_NAME)) {
                System.out.println("  Removing import: " + REMOVED_CLASS_NAME);
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
        
        private boolean isTargetFieldAccess(FieldAccessExpr expr) {
            // Check if the scope is a NameExpr with the target class name
            if (expr.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) expr.getScope();
                String scopeName = scope.getNameAsString();
                
                // Match either fully qualified name or simple name if imported
                return REMOVED_CLASS_NAME.equals("com.jcabi.aspects.Tv") && 
                       ("Tv".equals(scopeName) || "com.jcabi.aspects.Tv".equals(scopeName));
            }
            return false;
        }
    }
}