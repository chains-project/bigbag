package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to migrate javax.validation to jakarta.validation
 * This transformation handles both import statements and fully qualified references.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        SourceRoot root = new SourceRoot(sourcePath);
        
        // Parse all files 
        root.parse("", "");
        
        // Get all parsed units and apply transformation
        // Using a simpler approach without findAll
        root.getNodesByType(CompilationUnit.class).forEach(unit -> {
            if (unit != null) {
                ValidationMigrationVisitor visitor = new ValidationMigrationVisitor();
                unit.accept(visitor, null);
            }
        });
        
        // Save all modified files
        root.saveAll();
    }
    
    /**
     * Visitor that migrates javax.validation to jakarta.validation
     */
    static class ValidationMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Migrate import declarations
            if (importDecl.getNameAsString().startsWith("javax.validation")) {
                String newName = importDecl.getNameAsString().replaceFirst("javax.validation", "jakarta.validation");
                importDecl.setName(newName);
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            // Migrate fully qualified names 
            if (name.asString().startsWith("javax.validation")) {
                String newName = name.asString().replaceFirst("javax.validation", "jakarta.validation");
                return new Name(newName);
            }
            return super.visit(name, arg);
        }
    }
}