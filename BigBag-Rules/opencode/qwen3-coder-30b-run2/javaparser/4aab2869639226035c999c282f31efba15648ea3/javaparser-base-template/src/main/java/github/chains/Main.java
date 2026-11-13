package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation to fix breaking changes in asto-core dependency.
 * Specifically addresses the removal of Storages class in favor of StoragesLoader.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
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
            
            // Apply transformation to replace Storages with StoragesLoader
            StoragesTransformationVisitor visitor = new StoragesTransformationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms Storages.newStorage() calls to StoragesLoader.newObject() calls
     */
    private static class StoragesTransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr node, Void arg) {
            // Check if this is a call to newStorage method on Storages object
            if (node.getScope().isPresent()) {
                var scope = node.getScope().get();
                
                // Look for new Storages().newStorage(...) pattern
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                    if (creation.getType().getNameAsString().equals("Storages")) {
                        // Transform to StoragesLoader.newObject(...)
                        var newCreation = new ObjectCreationExpr();
                        newCreation.setType("com.artipie.asto.factory.StoragesLoader");
                        
                        // Replace the scope with new StoragesLoader creation
                        node.setScope(newCreation);
                        
                        // Change method name from newStorage to newObject
                        node.setName("newObject");
                        
                        return super.visit(node, arg);
                    }
                }
            }
            
            return super.visit(node, arg);
        }
    }
}