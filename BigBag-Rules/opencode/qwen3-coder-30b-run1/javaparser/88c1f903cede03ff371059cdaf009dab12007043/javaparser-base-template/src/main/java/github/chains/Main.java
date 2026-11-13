package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule to fix the zip4j API breaking change:
 * net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile
 * 
 * This rule can be applied to any Maven project affected by this breaking change.
 * Simply run: java Zip4jPackageRenameVisitor <path-to-java-file>
 */
public class Zip4jPackageRenameVisitor extends ModifierVisitor<Void> {
    
    /**
     * Updates import statements from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
     */
    @Override
    public void visit(ImportDeclaration importDeclaration, Void arg) {
        String importName = importDeclaration.getName().asString();
        if (importName.equals("net.lingala.zip4j.core.ZipFile")) {
            importDeclaration.setName("net.lingala.zip4j.ZipFile");
        }
        super.visit(importDeclaration, arg);
    }
    
    /**
     * Processes a single Java file by applying the transformation
     */
    public static void processFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        String content = new String(Files.readAllBytes(path));
        
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply the transformation
        cu.accept(new Zip4jPackageRenameVisitor(), null);
        
        // Write the updated content back to the file
        Files.write(path, cu.toString().getBytes());
        System.out.println("Updated file: " + filePath);
    }
    
    /**
     * Main entry point for the transformation tool
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Zip4jPackageRenameVisitor <path-to-java-file>");
            return;
        }
        
        try {
            processFile(args[0]);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}