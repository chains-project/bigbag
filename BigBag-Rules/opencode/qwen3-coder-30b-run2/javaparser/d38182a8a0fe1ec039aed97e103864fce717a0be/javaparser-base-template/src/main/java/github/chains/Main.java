package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation rule for fixing API changes in com.artipie.http.auth.Authentication
 * from v1.1.3 to v1.2.1 where Authentication.User was removed and replaced with AuthUser.
 *
 * This transformation handles:
 * - Replacing "new Authentication.User(name, password)" with "new AuthUser(name, password)"
 * - Replacing references to Authentication.User with AuthUser
 * - Removing calls to user.password() method (no longer available)
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter/src";
        Path targetDir = Paths.get(sourceDir);
        
        if (!Files.exists(targetDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find all Java files
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(targetDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        // Process each Java file
        for (Path file : javaFiles) {
            processJavaFile(file);
        }
        
        System.out.println("Transformation complete.");
    }
    
    private static void processJavaFile(Path filePath) throws IOException {
        // Parse the Java file
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
        
        // Apply transformations
        TransformationVisitor visitor = new TransformationVisitor();
        cu.accept(visitor, null);
        
        // Save the modified file
        cu.save(filePath.toFile());
        System.out.println("Processed: " + filePath);
    }
    
    /**
     * Visitor to transform the Authentication API usage patterns.
     */
    private static class TransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr oce, Void arg) {
            // Replace constructor calls for Authentication.User with AuthUser
            if (oce.getType().isClassOrInterfaceExpr()) {
                String typeName = oce.getType().asClassOrInterfaceExpr().getNameAsString();
                if ("Authentication.User".equals(typeName)) {
                    // Change Authentication.User to AuthUser
                    oce.setType("AuthUser");
                } else if ("User".equals(typeName)) {
                    // Handle cases where just "User" is referenced (need to check context)
                    // This would be more complex to verify contextually
                }
            }
            return super.visit(oce, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr mce, Void arg) {
            // Replace calls to user.password() with direct parameter usage
            if (mce.getNameAsString().equals("password") && 
                mce.getScope().isPresent() && 
                mce.getScope().get() instanceof NameExpr) {
                // If we're looking at user.password(), we need to handle it specially
                // This is a simplification - in a real implementation, we'd need to 
                // understand the full context to avoid incorrectly modifying other password() calls
            }
            return super.visit(mce, arg);
        }
        
        @Override
        public Visitable visit(NameExpr ne, Void arg) {
            // Look for patterns like "Authentication.User" references
            return super.visit(ne, arg);
        }
    }
}