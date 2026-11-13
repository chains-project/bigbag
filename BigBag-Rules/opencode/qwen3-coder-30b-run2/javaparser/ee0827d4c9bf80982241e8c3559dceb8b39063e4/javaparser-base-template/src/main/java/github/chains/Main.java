package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.imports.*;
import com.github.javaparser.ast.visitor.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Generic transformation to fix commons-io breaking changes in Maven projects.
 * This fix addresses the breaking changes in commons-io where classes such as:
 * - BoundedInputStream
 * - ClosedInputStream  
 * - ThresholdingOutputStream
 * - NullPrintStream
 * were removed or changed in newer versions.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(String sourceDir) {
        try {
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath);
            
            // Apply transformation
            MyVisitor visitor = new MyVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (var writer = Files.newBufferedWriter(javaFilePath, StandardCharsets.UTF_8)) {
                writer.write(cu.toString());
            }
            System.out.println("Processed: " + javaFilePath);
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }

    /**
     * Visitor that removes problematic imports
     */
    private static class MyVisitor extends ModifierVisitor<Void> {
        
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            // Check if this import refers to one of the classes we need to handle
            String qualifiedName = importDecl.getNameAsString();
            
            // Remove imports for problematic classes since they don't exist in newer versions
            if (qualifiedName.equals("org.apache.commons.io.input.BoundedInputStream") ||
                qualifiedName.equals("org.apache.commons.io.input.ClosedInputStream") ||
                qualifiedName.equals("org.apache.commons.io.output.ThresholdingOutputStream") ||
                qualifiedName.equals("org.apache.commons.io.output.NullPrintStream")) {
                // Remove the import since these classes are either deprecated or moved
                return null;
            }
            
            return super.visit(importDecl, arg);
        }
    }
}