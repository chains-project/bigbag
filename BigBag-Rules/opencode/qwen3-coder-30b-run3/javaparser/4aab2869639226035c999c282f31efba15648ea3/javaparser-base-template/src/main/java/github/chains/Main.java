package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

/**
 * Generic JavaParser transformation to fix breaking changes in asto-core dependency.
 * Specifically addresses removal of Storages class in favor of StoragesLoader usage.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            // Process all Java files in the directory recursively
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation completed.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix Storages usage
            StoragesFixVisitor visitor = new StoragesFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString().getBytes();
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes the breaking change from Storages to StoragesLoader
     */
    private static class StoragesFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check for creation of Storages class (which no longer exists)
            if (n.getType().getNameAsString().equals("Storages")) {
                // Replace with StoragesLoader constructor
                return new ObjectCreationExpr()
                    .setType("com.artipie.asto.factory.StoragesLoader");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for calls to newStorage that were on Storages objects
            if (n.getNameAsString().equals("newStorage") && n.getScope().isPresent()) {
                // Check if the scope is a Storages object creation
                if (n.getScope().get() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr objCreation = (ObjectCreationExpr) n.getScope().get();
                    if (objCreation.getType().getNameAsString().equals("Storages")) {
                        // Replace with newObject method call
                        // Convert the method call from newStorage(type, yamlMapping) to newObject(type, new YamlStorageConfig(yamlMapping))
                        MethodCallExpr newMethodCall = new MethodCallExpr(
                            new ObjectCreationExpr().setType("com.artipie.asto.factory.StoragesLoader"),
                            "newObject"
                        );
                        
                        // Add the arguments - first is type, second is config wrapper
                        if (n.getArguments().size() >= 2) {
                            // First argument is the type (same as before)
                            newMethodCall.addArgument(n.getArgument(0));
                            
                            // Second argument is the Config wrapper
                            ObjectCreationExpr configCreation = new ObjectCreationExpr()
                                .setType("com.artipie.asto.factory.Config.YamlStorageConfig")
                                .addArgument(n.getArgument(1));
                            newMethodCall.addArgument(configCreation);
                        } else if (n.getArguments().size() >= 1) {
                            // Handle case where there's only one argument
                            newMethodCall.addArgument(n.getArgument(0));
                        }
                        
                        return newMethodCall;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove import statement for Storages if present
            if (n.getNameAsString().equals("com.artipie.asto.factory.Storages")) {
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
    }
}
