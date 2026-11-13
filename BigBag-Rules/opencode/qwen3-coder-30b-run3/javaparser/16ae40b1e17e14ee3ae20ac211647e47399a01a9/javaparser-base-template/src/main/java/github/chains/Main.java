package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation to fix zip4j breaking changes.
 * Specifically addresses the removal of the 'core' package in zip4j 2.11.1+
 * Changes: net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile
 */
public class Main {
    
    public static void main(String[] args) {
        // Usage: java Main /path/to/source/directory
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix zip4j import statements
            boolean modified = fixZip4jImports(cu);
            
            if (modified) {
                // Save the modified file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Fixed file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to process file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Fix zip4j import statements by removing 'core' from package names
     */
    private static boolean fixZip4jImports(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all import declarations
        List<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration imp : imports) {
            Name name = imp.getName();
            String qualifiedName = name.asString();
            
            // Check if this is a zip4j.core import that needs to be fixed
            if (qualifiedName.startsWith("net.lingala.zip4j.core.")) {
                // Replace with the new package name without 'core'
                String newQualifiedName = qualifiedName.replace("net.lingala.zip4j.core.", "net.lingala.zip4j.");
                
                // Update import declaration
                imp.setName(newQualifiedName);
                modified = true;
            }
        }
        
        return modified;
    }
}
