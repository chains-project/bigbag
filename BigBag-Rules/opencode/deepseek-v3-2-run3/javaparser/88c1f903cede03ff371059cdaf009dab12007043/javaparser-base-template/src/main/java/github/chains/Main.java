package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.PrettyPrinter;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Process all imports
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (int i = 0; i < imports.size(); i++) {
                ImportDeclaration importDecl = imports.get(i);
                String importName = importDecl.getNameAsString();
                
                // Check if this is a zip4j.core import that needs to be transformed
                if (importName.startsWith("net.lingala.zip4j.core.")) {
                    // Transform: net.lingala.zip4j.core.X -> net.lingala.zip4j.X
                    String newImportName = importName.replaceFirst("net\\.lingala\\.zip4j\\.core\\.", "net.lingala.zip4j.");
                    
                    // Create new import declaration
                    ImportDeclaration newImport = new ImportDeclaration(
                        new Name(newImportName),
                        importDecl.isStatic(),
                        importDecl.isAsterisk()
                    );
                    
                    // Replace the old import with the new one
                    imports.set(i, newImport);
                    modified = true;
                    
                    System.out.println("Transformed import in " + filePath + ":");
                    System.out.println("  Old: " + importName);
                    System.out.println("  New: " + newImportName);
                }
            }
            
            if (modified) {
                // Pretty print the transformed code
                String transformedCode = new PrettyPrinter().print(cu);
                Files.write(filePath, transformedCode.getBytes());
                
                System.out.println("Updated file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}