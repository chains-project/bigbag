package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the provided directory
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix zip4j imports
            boolean modified = fixZip4jImport(cu);
            
            if (modified) {
                // Write back to file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Fixes the zip4j import statement to use the correct package path
     * from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
     */
    private static boolean fixZip4jImport(CompilationUnit cu) {
        boolean modified = false;
        
        // Visit all import declarations
        for (ImportDeclaration importDecl : cu.getImports()) {
            // Check if this is the problematic zip4j.core.ZipFile import
            if (importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                // Replace with correct import path
                importDecl.setName(new Name("net.lingala.zip4j.ZipFile"));
                modified = true;
            }
        }
        
        return modified;
    }
}
