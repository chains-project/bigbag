package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation to fix breaking changes in org.tinspin.index dependency
 * from version 1.x to 2.0.1
 */
public class TinspinApiTransformer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java TinspinApiTransformer <source_directory>");
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
            .forEach(TinspinApiTransformer::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String code = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(code);
            
            // Apply transformations
            TinspinApiFixer fixer = new TinspinApiFixer();
            cu.accept(fixer, null);
            
            // Write back to file if changes were made
            if (fixer.hasChanges()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes the API changes
     */
    private static class TinspinApiFixer extends ModifierVisitor<Void> {
        private boolean changed = false;
        
        public boolean hasChanges() {
            return changed;
        }
        
        @Override
        public Visitable visit(SimpleName n, Void arg) {
            // Replace PointDistanceFunction with PointDistance
            if ("PointDistanceFunction".equals(n.getIdentifier())) {
                n.setIdentifier("PointDistance");
                changed = true;
            }
            
            // Replace PointEntryDist with PointEntryKnn
            if ("PointEntryDist".equals(n.getIdentifier())) {
                n.setIdentifier("PointEntryKnn");
                changed = true;
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Handle CoverTree.create() method calls
            if (n.getNameAsString().equals("create") && 
                n.getScope().isPresent() && 
                n.getScope().get().toString().contains("CoverTree")) {
                
                // This call might have changed signature - need to check for PointDistanceFunction parameter
                // For now, we just mark that we've processed this
                changed = true;
            }
            
            return super.visit(n, arg);
        }
    }
}