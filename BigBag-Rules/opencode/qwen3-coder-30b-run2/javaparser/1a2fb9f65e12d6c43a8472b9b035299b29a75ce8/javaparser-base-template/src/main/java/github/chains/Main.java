package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Get source directory from command line arguments
        if (args.length == 0) {
            System.err.println("Please provide the source directory path as argument");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path rootDir = Paths.get(sourceDir);
        
        try {
            // Process all Java files recursively
            Files.walk(rootDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation to fix javax.validation imports
            boolean modified = transformCompilationUnit(cu);
            
            // If any changes were made, save the file
            if (modified) {
                // Write back to file with updated content
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // List to store imports to be removed
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        
        // Collect imports that need to be replaced
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // Check for javax.validation imports
            if (importName.startsWith("javax.validation")) {
                // Remove the old import
                importsToRemove.add(importDecl);
                
                // Determine the new import path
                String newImportName = importName.replaceFirst("^javax\\.validation", "jakarta.validation");
                
                // Add the new import
                ImportDeclaration newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                cu.addImport(newImport);
                modified = true;
            }
        }
        
        // Remove the old imports
        for (ImportDeclaration importToRemove : importsToRemove) {
            cu.remove(importToRemove);
        }
        
        return modified;
    }
}