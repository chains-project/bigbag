package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to migrate javax.validation to jakarta.validation
 * This is a reusable rule that can be applied to any Maven project affected by
 * the breaking dependency update from javax.validation to jakarta.validation.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Default path - can be overridden via command line arguments
        String sourceDir = "/workspace/wicket-crudifier";
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        System.out.println("Applying javax.validation to jakarta.validation migration to: " + sourceDir);
        
        // Process all Java files in the source directory
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to migrate javax.validation to jakarta.validation
            MigrationVisitor visitor = new MigrationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file back
            String output = cu.toString();
            Files.write(filePath, output.getBytes(StandardCharsets.UTF_8));
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
/**
 * Visitor that migrates javax.validation to jakarta.validation package references
 */
private static class MigrationVisitor extends ModifierVisitor<Void> {
    
    @Override
    public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
        // Check if this is a javax.validation import and replace it
        String importName = importDecl.getNameAsString();
        if (importName.startsWith("javax.validation")) {
            // Replace javax.validation with jakarta.validation
            String newImportName = importName.replace("javax.validation", "jakarta.validation");
            importDecl.setName(newImportName);
            System.out.println("  Updated import: " + importName + " -> " + newImportName);
        }
        return importDecl;
    }
}
}
