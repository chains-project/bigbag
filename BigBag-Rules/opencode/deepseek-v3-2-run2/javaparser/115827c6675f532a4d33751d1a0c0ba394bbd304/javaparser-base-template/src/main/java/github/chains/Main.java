package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for javax.servlet to jakarta.servlet migration.
 * This transformation can be applied to ANY Maven project affected by the same breaking change.
 * 
 * Breaking Change Pattern:
 * - Old API: javax.servlet.*
 * - New API: jakarta.servlet.*
 * - Transformation: Simple package name replacement
 */
public class Main {
    
    // Configuration parameters - can be changed for different projects
    private static final String OLD_PACKAGE_PREFIX = "javax.servlet";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.servlet";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying javax.servlet -> jakarta.servlet transformation to: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDirectory));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        return Files.walk(rootDir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        boolean wasModified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(new Name(newImportName));
                wasModified = true;
                System.out.println("  - Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        // Transform fully qualified names in code using visitor pattern
        ServletPackageVisitor visitor = new ServletPackageVisitor();
        cu.accept(visitor, null);
        if (visitor.wasModified()) {
            wasModified = true;
        }
        
        if (wasModified) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("✓ Transformed: " + filePath);
            return true;
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms javax.servlet package references in code
     * This handles fully qualified names used in method calls, field declarations, etc.
     */
    private static class ServletPackageVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameAsString = name.asString();
            if (nameAsString.startsWith(OLD_PACKAGE_PREFIX)) {
                String newName = nameAsString.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                modified = true;
                System.out.println("  - Updated qualified name: " + nameAsString + " -> " + newName);
                return new Name(newName);
            }
            return super.visit(name, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}