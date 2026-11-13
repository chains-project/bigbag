package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule to fix zip4j.core package relocation breaking change.
 * 
 * This transformation fixes the breaking change introduced in zip4j 2.11.0 where the
 * net.lingala.zip4j.core package was removed and classes moved directly to net.lingala.zip4j.
 * 
 * Old: import net.lingala.zip4j.core.ZipFile;
 * New: import net.lingala.zip4j.ZipFile;
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            // Process all Java files in the directory recursively
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Process a single Java file to fix zip4j.core import statements
     */
    private static void processJavaFile(Path filePath) {
        try {
            SourceRoot sourceRoot = new SourceRoot(filePath.getParent());
            CompilationUnit cu = sourceRoot.parse("", filePath.getFileName().toString());
            
            // Apply the transformation
            new Zip4jCoreRelocationTransformer().visit(cu, null);
            
            // Save the modified file using FileWriter
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms zip4j.core import statements to net.lingala.zip4j
     */
    private static class Zip4jCoreRelocationTransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Check if this import is from the zip4j.core package
            if (importDecl.getNameAsString().startsWith("net.lingala.zip4j.core")) {
                // Transform the import to use the new package structure
                String oldImport = importDecl.getNameAsString();
                
                // Replace net.lingala.zip4j.core with net.lingala.zip4j
                String newImport = oldImport.replace("net.lingala.zip4j.core", "net.lingala.zip4j");
                
                // Update the import declaration
                importDecl.setName(newImport);
            }
        }
    }
}
