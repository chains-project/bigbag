package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Simple test program to demonstrate zip4j import fix approach.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try {
            // Create SourceRoot to parse all Java files
            SourceRoot root = new SourceRoot(sourcePath);
            
            // Parse all Java files in the directory (simple approach)
            List<CompilationUnit> units = root.parse(sourceDirectory, ""); 
            
            // Process each compilation unit and transform imports
            boolean transformed = false;
            for (CompilationUnit unit : units) {
                // Visit and transform imports
                List<ImportDeclaration> imports = unit.getImports();
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    if ("net.lingala.zip4j.core.ZipFile".equals(importName)) {
                        // Change import from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
                        importDecl.setName("net.lingala.zip4j.ZipFile");
                        transformed = true;
                    }
                }
            }
            
            // Save all files if there were transformations
            if (transformed) {
                root.saveAll();
                System.out.println("Transformation completed successfully.");
            } else {
                System.out.println("No matching imports found.");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error parsing files: " + e.getMessage());
            System.exit(1);
        }
    }
}