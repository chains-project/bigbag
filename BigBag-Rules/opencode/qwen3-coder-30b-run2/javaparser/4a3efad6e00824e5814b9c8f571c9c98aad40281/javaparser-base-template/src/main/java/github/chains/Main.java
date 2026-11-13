package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            // Process all Java files in the source directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Read the original file content
            String originalContent = Files.readString(filePath);
            
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Check for the problematic import and update it
            boolean modified = false;
            
            // Find import statements that import CertificationPermission from pades package
            Optional<ImportDeclaration> oldImport = cu.getImports()
                .stream()
                .filter(importDecl -> 
                    importDecl.getNameAsString().equals("eu.europa.esig.dss.pades.CertificationPermission"))
                .findFirst();
            
            if (oldImport.isPresent()) {
                // Create new content with corrected import
                String newContent = originalContent.replace(
                    "import eu.europa.esig.dss.pades.CertificationPermission;",
                    "import eu.europa.esig.dss.enumerations.CertificationPermission;"
                );
                
                // Write back the updated content
                Files.writeString(filePath, newContent);
                modified = true;
            }
            
            if (modified) {
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}