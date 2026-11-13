package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation to migrate from javax.validation to jakarta.validation
 * due to breaking changes in Hibernate Validator 8.0+
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
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path javaFilePath : javaFiles) {
                try {
                    // Parse the Java file
                    CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
                    
                    // Apply the transformation
                    ValidationMigrationVisitor visitor = new ValidationMigrationVisitor();
                    cu.accept(visitor, null);
                    
                    // Save the modified file
                    cu.toString(); // Get the modified content
                    Files.write(javaFilePath, cu.toString().getBytes());
                } catch (Exception e) {
                    System.err.println("Failed to process file: " + javaFilePath + " - " + e.getMessage());
                }
            }
        }
        System.out.println("Validation migration completed.");
    }

    /**
     * Visitor that migrates javax.validation to jakarta.validation
     */
    private static class ValidationMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Replace javax.validation imports with jakarta.validation
            if ("javax.validation.Valid".equals(importDecl.getNameAsString())) {
                importDecl.setName("jakarta.validation.Valid");
                return importDecl;
            } else if (importDecl.getNameAsString().startsWith("javax.validation.")) {
                // Handle other javax.validation imports like javax.validation.constraints.*
                String newName = importDecl.getNameAsString().replace("javax.validation", "jakarta.validation");
                importDecl.setName(newName);
                return importDecl;
            }
            return super.visit(importDecl, arg);
        }
    }
}
