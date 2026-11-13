package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for zip4j package restructuring from version 1.x to 2.x
 * 
 * This transformation removes the ".core" subpackage from:
 * - Import declarations: net.lingala.zip4j.core.* → net.lingala.zip4j.*
 * - Type references in code: net.lingala.zip4j.core.ZipFile → net.lingala.zip4j.ZipFile
 * 
 * The transformation is parameterized by:
 * - OLD_PACKAGE_PREFIX: The old package prefix to match
 * - NEW_PACKAGE_PREFIX: The new package prefix to replace with
 * - SUBPACKAGE_TO_REMOVE: The subpackage to remove from the old structure
 */
public class Main {
    
    // Configuration parameters - these can be changed to handle different package restructurings
    private static final String OLD_PACKAGE_PREFIX = "net.lingala.zip4j";
    private static final String NEW_PACKAGE_PREFIX = "net.lingala.zip4j";
    private static final String SUBPACKAGE_TO_REMOVE = "core";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            System.out.println("Transformation completed: Removed '" + SUBPACKAGE_TO_REMOVE + 
                             "' subpackage from " + OLD_PACKAGE_PREFIX + " imports and type references");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        PackageRestructureVisitor visitor = new PackageRestructureVisitor();
        cu.accept(visitor, null);
        
        // Check if any modifications were made
        boolean modified = visitor.wasModified();
        
        if (modified) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms package references by removing the specified subpackage
     */
    private static class PackageRestructureVisitor extends GenericVisitorAdapter<Void, Void> {
        private boolean modified = false;
        
        @Override
        public Void visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches the old package structure
            if (importName.startsWith(OLD_PACKAGE_PREFIX + "." + SUBPACKAGE_TO_REMOVE + ".")) {
                // Remove the subpackage from the import
                String newImportName = importName.replaceFirst(
                    OLD_PACKAGE_PREFIX + "\\." + SUBPACKAGE_TO_REMOVE + "\\.", 
                    NEW_PACKAGE_PREFIX + "."
                );
                
                System.out.println("  Transforming import: " + importName + " → " + newImportName);
                importDecl.setName(new Name(newImportName));
                modified = true;
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Void visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check if this type reference matches the old package structure
            // We need to handle both fully qualified names and simple names that might be imported
            if (typeName.startsWith(OLD_PACKAGE_PREFIX + "." + SUBPACKAGE_TO_REMOVE + ".")) {
                // Remove the subpackage from the type reference
                String newTypeName = typeName.replaceFirst(
                    OLD_PACKAGE_PREFIX + "\\." + SUBPACKAGE_TO_REMOVE + "\\.", 
                    NEW_PACKAGE_PREFIX + "."
                );
                
                System.out.println("  Transforming type: " + typeName + " → " + newTypeName);
                type.setName(newTypeName);
                modified = true;
            }
            
            return super.visit(type, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}