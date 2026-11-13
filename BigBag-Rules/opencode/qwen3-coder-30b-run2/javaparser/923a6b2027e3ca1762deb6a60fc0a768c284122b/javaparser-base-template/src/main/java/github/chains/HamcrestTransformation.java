package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation for migrating hamcrest library usage.
 * This handles breaking changes from hamcrest 1.x to 2.2+
 * where packages and class locations have changed.
 */
public class HamcrestTransformation {

    /**
     * Apply the hamcrest transformation to a file.
     * @param filePath Path to the Java file to transform
     * @throws IOException if file cannot be read or written
     */
    public static void applyTransformation(String filePath) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath));
        
        // Apply transformation to imports and other hamcrest references
        cu.accept(new HamcrestImportVisitor(), null);
        
        // Write back the transformed file
        try (FileWriter writer = new FileWriter(filePath)) {
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(CompilationUnit cu, Void arg) {
                    try {
                        writer.write(cu.toString());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, null);
        }
    }
    
    /**
     * Visitor to handle hamcrest import transformations.
     */
    private static class HamcrestImportVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Check for hamcrest-library imports
            if (importDecl.getNameAsString().startsWith("org.hamcrest.library")) {
                // Change to standard hamcrest import
                String newImport = importDecl.getNameAsString()
                    .replace("org.hamcrest.library", "org.hamcrest");
                importDecl.setName(newImport);
            }
            
            // Check for hamcrest-core imports
            if (importDecl.getNameAsString().startsWith("org.hamcrest.core")) {
                // Change to standard hamcrest import
                String newImport = importDecl.getNameAsString()
                    .replace("org.hamcrest.core", "org.hamcrest");
                importDecl.setName(newImport);
            }
        }
    }
    
    /**
     * Check if a file contains hamcrest imports.
     * @param filePath Path to the Java file to check
     * @return true if hamcrest imports are found
     * @throws IOException if file cannot be read
     */
    public static boolean hasHamcrestImports(String filePath) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath));
        return cu.getImports().stream()
            .anyMatch(importDecl -> 
                importDecl.getNameAsString().startsWith("org.hamcrest"));
    }
    
    /**
     * Apply transformation to all Java files in a directory recursively.
     * @param directoryPath Path to the directory containing Java files
     * @throws IOException if files cannot be processed
     */
    public static void applyTransformationToDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    applyTransformation(path.toString());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to transform " + path, e);
                }
            });
    }
}