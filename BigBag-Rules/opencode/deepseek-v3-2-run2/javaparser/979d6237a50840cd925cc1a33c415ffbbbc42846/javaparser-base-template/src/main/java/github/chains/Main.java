package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Configuration for the package rename transformation.
     * This should be parameterized for different breaking changes.
     */
    public static class PackageRenameConfig {
        private final String oldPackagePrefix;
        private final String newPackagePrefix;
        
        public PackageRenameConfig(String oldPackagePrefix, String newPackagePrefix) {
            this.oldPackagePrefix = oldPackagePrefix;
            this.newPackagePrefix = newPackagePrefix;
        }
        
        public String getOldPackagePrefix() { return oldPackagePrefix; }
        public String getNewPackagePrefix() { return newPackagePrefix; }
        
        /**
         * Check if a fully qualified name matches the old package pattern.
         */
        public boolean matchesOldPackage(String fullyQualifiedName) {
            return fullyQualifiedName.startsWith(oldPackagePrefix);
        }
        
        /**
         * Transform a fully qualified name from old to new package.
         */
        public String transformPackage(String fullyQualifiedName) {
            if (matchesOldPackage(fullyQualifiedName)) {
                return newPackagePrefix + fullyQualifiedName.substring(oldPackagePrefix.length());
            }
            return fullyQualifiedName;
        }
    }
    
    /**
     * Visitor that transforms package references in Java source files.
     */
    public static class PackageRenameVisitor extends ModifierVisitor<Void> {
        private final PackageRenameConfig config;
        
        public PackageRenameVisitor(PackageRenameConfig config) {
            this.config = config;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (config.matchesOldPackage(importName)) {
                String newImportName = config.transformPackage(importName);
                importDecl.setName(newImportName);
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String qualifiedName = name.toString();
            
            // Check if this name represents a fully qualified type reference
            if (config.matchesOldPackage(qualifiedName)) {
                String newName = config.transformPackage(qualifiedName);
                // Parse the new name to handle nested names correctly
                Name newNameNode = new Name(newName);
                return newNameNode;
            }
            
            return (Node) super.visit(name, arg);
        }
    }
    
    /**
     * Process a single Java file with the package rename transformation.
     */
    public static void processFile(Path filePath, PackageRenameConfig config) throws IOException {
        System.out.println("Processing: " + filePath);
        
        String content = Files.readString(filePath);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + filePath)
        );
        
        PackageRenameVisitor visitor = new PackageRenameVisitor(config);
        cu.accept(visitor, null);
        
        // Get the modified content
        String newContent = cu.toString();
        
        if (!content.equals(newContent)) {
            System.out.println("  ✓ Updated package references");
            Files.write(filePath, newContent.getBytes());
        } else {
            System.out.println("  ✓ No changes needed");
        }
    }
    
    /**
     * Find all Java files in a directory recursively.
     */
    public static List<Path> findJavaFiles(Path directory) throws IOException {
        return Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }
    
    /**
     * Main entry point for the transformation.
     * 
     * Expected arguments:
     * 1. Source directory path
     * 2. Old package prefix (e.g., "org.apache.struts2.dispatcher.ng.filter")
     * 3. New package prefix (e.g., "org.apache.struts2.dispatcher.filter")
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java github.chains.Main <source-directory> <old-package-prefix> <new-package-prefix>");
            System.err.println("Example: java github.chains.Main /path/to/project org.apache.struts2.dispatcher.ng.filter org.apache.struts2.dispatcher.filter");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackagePrefix = args[1];
        String newPackagePrefix = args[2];
        
        System.out.println("Starting package rename transformation:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Old package: " + oldPackagePrefix);
        System.out.println("  New package: " + newPackagePrefix);
        System.out.println();
        
        PackageRenameConfig config = new PackageRenameConfig(oldPackagePrefix, newPackagePrefix);
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
                System.exit(1);
            }
            
            List<Path> javaFiles = findJavaFiles(sourcePath);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            System.out.println();
            
            int processed = 0;
            int updated = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    processFile(javaFile, config);
                    processed++;
                    // Check if file was actually modified by comparing before/after
                    // (simplified - actual modification check is done in processFile)
                    if (processed % 10 == 0) {
                        System.out.println("Processed " + processed + " files...");
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println();
            System.out.println("Transformation complete!");
            System.out.println("Processed " + processed + " Java files");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}