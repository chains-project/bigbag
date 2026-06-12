package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing CloudResourceManager API breaking changes.
 * This tool can be applied to any Maven project affected by the same breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing directory: " + sourceDirectory);
        
        try {
            processDirectory(sourceDirectory);
            System.out.println("Migration completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during migration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process all Java files in the given directory
     */
    public static void processDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        Files.walk(dir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processJavaFile(path);
                } catch (Exception e) {
                    System.err.println("Error processing file: " + path + " - " + e.getMessage());
                }
            });
    }
    
    /**
     * Process a single Java file to fix CloudResourceManager API issues
     */
    public static void processJavaFile(Path filePath) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        
        // Fix imports that reference the old model package
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration imp : imports) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("com.google.api.services.cloudresourcemanager.model")) {
                // Update to newer package structure
                String newImport = importName.replace(
                    "com.google.api.services.cloudresourcemanager.model",
                    "com.google.api.services.cloudresourcemanager.v3.model"
                );
                imp.setName(newImport);
                modified = true;
                System.out.println("Updated import in " + filePath + ": " + importName + " -> " + newImport);
            }
        }
        
        // If we made changes, write back the file
        if (modified) {
            Files.write(filePath, cu.toString().getBytes());
        }
    }
}
