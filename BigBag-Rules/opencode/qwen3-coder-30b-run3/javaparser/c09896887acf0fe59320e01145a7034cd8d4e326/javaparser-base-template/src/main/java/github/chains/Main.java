package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix breaking changes in GitHub API
 * Specifically addresses the issue where GHCompare.status became private and 
 * requires a getStatus() method call instead of direct field access.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path basePath = Paths.get(sourceDir);
        
        // Find all Java files in the directory
        Files.walk(basePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation
            GHCompareStatusFixVisitor visitor = new GHCompareStatusFixVisitor();
            cu.accept(visitor, null);
            
            // Write back if changes were made
            if (visitor.hasChanges()) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Fixed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces direct field access to .status with .getStatus() method call
     * for GHCompare objects.
     */
    private static class GHCompareStatusFixVisitor extends ModifierVisitor<Void> {
        private boolean changesMade = false;
        
        @Override
        public Visitable visit(FieldAccessExpr fieldAccessExpr, Void arg) {
            // Match pattern: someVar.status (where someVar is of type GHCompare)
            // This is a conservative pattern that matches any .status access
            // and transforms it to .getStatus() to fix the breaking change
            if (fieldAccessExpr.getNameAsString().equals("status")) {
                // Transform to: someVar.getStatus()
                MethodCallExpr newCall = new MethodCallExpr(
                    fieldAccessExpr.getScope(), 
                    "getStatus"
                );
                
                changesMade = true;
                return newCall;
            }
            
            return super.visit(fieldAccessExpr, arg);
        }
        
        public boolean hasChanges() {
            return changesMade;
        }
    }
}