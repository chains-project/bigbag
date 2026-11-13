package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Check if source directory is provided
        if (args.length == 0) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation
            cu.accept(new JakartaValidationTransformer(), null);
            
            // Save back to file
            cu.save(filePath.toFile());
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Transformer that replaces javax.validation imports and annotations with jakarta.validation
     */
    private static class JakartaValidationTransformer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            // Remove javax.validation imports
            NodeList<ImportDeclaration> importsToRemove = new NodeList<>();
            cu.getImports().forEach(importDecl -> {
                if (importDecl.getNameAsString().startsWith("javax.validation")) {
                    importsToRemove.add(importDecl);
                }
            });
            cu.getImports().removeAll(importsToRemove);
            
            // Add jakarta.validation import if not already present
            boolean hasJakartaValidation = cu.getImports().stream()
                    .anyMatch(importDecl -> 
                        importDecl.getNameAsString().startsWith("jakarta.validation"));
            
            if (!hasJakartaValidation) {
                cu.addImport("jakarta.validation");
            }
            
            // Replace annotations at method level
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                method.getAnnotations().forEach(annotation -> {
                    if ("javax.validation.Valid".equals(annotation.getNameAsString())) {
                        annotation.setName("jakarta.validation.Valid");
                    }
                });
            });
            
            return super.visit(cu, arg);
        }
    }
}