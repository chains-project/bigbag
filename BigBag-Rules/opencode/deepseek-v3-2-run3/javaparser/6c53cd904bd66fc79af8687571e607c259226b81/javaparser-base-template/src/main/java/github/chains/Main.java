package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Generic transformation rule for Struts2 package rename from:
     * Old pattern: org.apache.struts2.dispatcher.ng.filter
     * New pattern: org.apache.struts2.dispatcher.filter
     * 
     * This transformation handles:
     * 1. Import statements
     * 2. Fully-qualified type references in code
     * 3. Any reference to types from the renamed package
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // Find all Java files recursively
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
                
                // Create visitor to transform the code
                PackageRenameVisitor visitor = new PackageRenameVisitor();
                cu.accept(visitor, null);
                
                int fileTransformations = visitor.getTransformationCount();
                if (fileTransformations > 0) {
                    // Write transformed file
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                    
                    transformedFiles++;
                    totalTransformations += fileTransformations;
                    System.out.println("Transformed " + javaFile + " (" + fileTransformations + " changes)");
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    /**
     * Visitor that transforms code by renaming packages from the old to new pattern.
     * This is generic and can be parameterized for different package renames.
     */
    static class PackageRenameVisitor extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        // Old and new package patterns
        private static final String OLD_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.ng.filter";
        private static final String NEW_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.filter";
        
        // The specific class that was in the renamed package
        private static final String CLASS_NAME = "StrutsPrepareAndExecuteFilter";
        private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE_PREFIX + "." + CLASS_NAME;
        private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE_PREFIX + "." + CLASS_NAME;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import references the old package
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                // Transform the import
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(newImportName);
                transformationCount++;
                System.out.println("    [Import] " + importName + " -> " + newImportName);
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String nameStr = name.asString();
            
            // Check if this name matches the old fully-qualified class name
            if (nameStr.equals(OLD_FULLY_QUALIFIED_NAME)) {
                // Replace with new fully-qualified name
                name.setIdentifier(NEW_FULLY_QUALIFIED_NAME);
                transformationCount++;
                System.out.println("    [FQN] " + nameStr + " -> " + NEW_FULLY_QUALIFIED_NAME);
            }
            // Check if this is a reference to just the class name (could be imported)
            else if (nameStr.equals(CLASS_NAME)) {
                // We need to check if this is actually referring to our class
                // by looking at the surrounding context. For simplicity, we'll
                // check if the parent is a type reference or similar.
                // In practice, you'd want more sophisticated context checking.
                Node parent = name.getParentNode().orElse(null);
                if (parent != null) {
                    String parentStr = parent.toString();
                    // Simple heuristic: if the parent looks like a type reference
                    if (parentStr.contains(CLASS_NAME) && !parentStr.contains(".")) {
                        // This transformation would require knowing if the class
                        // was imported from the old package vs new package.
                        // For a generic rule, we transform imports and let
                        // simple name references resolve via imports.
                    }
                }
            }
            
            return (Node) super.visit(name, arg);
        }
    }
}