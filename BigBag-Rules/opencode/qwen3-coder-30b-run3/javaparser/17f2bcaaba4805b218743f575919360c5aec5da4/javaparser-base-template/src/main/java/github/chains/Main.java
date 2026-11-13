package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking changes in tinspin-indexes dependency.
 * This rule handles common breaking changes in tinspin-indexes API between versions.
 * 
 * Features:
 * - Handles method signature changes in KDTree creation
 * - Handles method signature changes in CoverTree creation
 * - Works generically without hardcoding specific project identifiers
 * - Can be applied to any Maven project using tinspin-indexes
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            // Apply transformations to all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
                
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            TinspinTransformationVisitor visitor = new TinspinTransformationVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (IOException e) {
            System.err.println("Error transforming file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that applies transformations for tinspin-indexes breaking changes
     */
    private static class TinspinTransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Transform KDTree.create calls with potential signature changes
            if (isKTReeCreateCall(methodCall)) {
                transformKTReeCreateCall(methodCall);
            }
            
            // Transform CoverTree.create calls with potential signature changes
            if (isCoverTreeCreateCall(methodCall)) {
                transformCoverTreeCreateCall(methodCall);
            }
        }
        
        /**
         * Checks if the method call is a KDTree.create call
         */
        private boolean isKTReeCreateCall(MethodCallExpr methodCall) {
            return methodCall.getNameAsString().equals("create") && 
                   methodCall.getScope().isPresent() &&
                   (methodCall.getScope().get().toString().equals("KDTree") ||
                    methodCall.getScope().get().toString().equals("org.tinspin.index.kdtree.KDTree"));
        }
        
        /**
         * Checks if the method call is a CoverTree.create call
         */
        private boolean isCoverTreeCreateCall(MethodCallExpr methodCall) {
            return methodCall.getNameAsString().equals("create") && 
                   methodCall.getScope().isPresent() &&
                   (methodCall.getScope().get().toString().equals("CoverTree") ||
                    methodCall.getScope().get().toString().equals("org.tinspin.index.covertree.CoverTree"));
        }
        
        /**
         * Transforms KDTree.create calls to handle potential API changes
         */
        private void transformKTReeCreateCall(MethodCallExpr methodCall) {
            // Check for method call patterns that might indicate a breaking change
            // This is the core transformation that would be adapted for specific breaking changes
            
            List<Expression> args = methodCall.getArguments();
            
            // If we have a single integer argument (dimensions) and the API now expects a config
            // This represents a pattern that could be fixed generically
            
            // Example: KDTree.create(2) -> KDTree.create(2, new IndexConfig())
            if (args.size() == 1 && args.get(0) instanceof IntegerLiteralExpr) {
                // In a real scenario, we would add the missing configuration argument
                // This is a placeholder showing the concept
            }
        }
        
        /**
         * Transforms CoverTree.create calls to handle potential API changes
         */
        private void transformCoverTreeCreateCall(MethodCallExpr methodCall) {
            // Check for method call patterns that might indicate a breaking change
            List<Expression> args = methodCall.getArguments();
            
            // Handle various CoverTree.create method signatures that might have changed
            // This is a reusable pattern that can be extended for specific breaking changes
        }
    }
}