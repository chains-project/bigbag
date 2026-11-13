package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.File;
import java.util.List;

/**
 * Simple transformation to migrate javax.jws to jakarta.jws imports.
 */
public class Main {
    
    public static void main(String[] args) {
        // Default to current directory if no argument provided
        String sourcePath = args.length > 0 ? args[0] : ".";
        
        try {
            // Process all Java files recursively
            File dir = new File(sourcePath);
            processDirectory(dir);
            
            System.out.println("JWS migration completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during JWS migration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }
    
    private static void processJavaFile(File file) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply transformation
            JWSMigrationVisitor visitor = new JWSMigrationVisitor();
            cu.accept(visitor, null);
            
            // Save the file back
            cu.save(file);
        } catch (Exception e) {
            System.err.println("Failed to process file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor class that performs the javax.jws to jakarta.jws migration
     */
    private static class JWSMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            // Check if this is a javax.jws import that needs to be migrated
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.jws")) {
                // Transform javax.jws imports to jakarta.jws
                String newImportName = importName.replace("javax.jws", "jakarta.jws");
                importDecl.setName(newImportName);
            }
            
            return super.visit(importDecl, arg);
        }
    }
}
