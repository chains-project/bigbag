package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    /**
     * Generic transformation rule for migrating from javax.validation to jakarta.validation
     * This rule handles the Java EE to Jakarta EE namespace change.
     * 
     * Transformation pattern:
     * - Old: javax.validation.*
     * - New: jakarta.validation.*
     * 
     * This affects:
     * 1. Import statements
     * 2. Fully-qualified type references in code
     * 3. Static imports
     */
    public static class JakartaValidationMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import starts with javax.validation
            if (importName.startsWith("javax.validation")) {
                // Replace javax.validation with jakarta.validation
                String newImportName = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(new Name(newImportName));
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String nameStr = name.asString();
            
            // Check if this is a fully-qualified name that starts with javax.validation
            // This handles cases like: javax.validation.Validator validator = ...
            if (nameStr.startsWith("javax.validation.")) {
                String newName = nameStr.replace("javax.validation", "jakarta.validation");
                return new Name(newName);
            }
            
            return (Node) super.visit(name, arg);
        }
    }
    
    /**
     * Process a single Java file
     */
    private static void processFile(Path filePath) throws IOException {
        System.out.println("Processing: " + filePath);
        
        // Read the original content
        String originalContent = Files.readString(filePath);
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(originalContent).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + filePath)
        );
        
        JakartaValidationMigrationVisitor visitor = new JakartaValidationMigrationVisitor();
        cu.accept(visitor, null);
        
        // Write the modified file
        Files.writeString(filePath, cu.toString());
    }
    
    /**
     * Find all Java files in a directory recursively
     */
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Main entry point
     * Usage: java github.chains.Main <source-directory>
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This tool migrates javax.validation imports to jakarta.validation");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int processed = 0;
            int modified = 0;
            
            for (Path javaFile : javaFiles) {
                processed++;
                
                // Read file to check if it contains javax.validation
                String content = Files.readString(javaFile);
                if (content.contains("javax.validation")) {
                    processFile(javaFile);
                    modified++;
                    System.out.println("  -> Modified: " + javaFile);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Total files processed: " + processed);
            System.out.println("  Files modified: " + modified);
            System.out.println("  Migration complete!");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}