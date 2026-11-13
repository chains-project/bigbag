package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix plexus-utils breaking changes
 * Specifically addresses the relocation of Xpp3Dom from org.codehaus.plexus.util.xml to org.codehaus.plexus.util
 * 
 * This transformation replaces import statements from:
 *   org.codehaus.plexus.util.xml.Xpp3Dom
 * to:
 *   org.codehaus.plexus.util.Xpp3Dom
 * 
 * This is a reusable rule that can be applied to any Maven project affected by the same breaking change
 * in plexus-utils 4.0.0.
 */
public class Main {
    
    public static void main(String[] args) {
        // Define the source directory to process
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/depclean";
        
        try {
            // Process all Java files in the directory
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix the import
            new ImportFixerVisitor().visit(cu, null);
            
            // Print successful processing
            System.out.println("Processed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix imports from the old xml package to main util package
     */
    private static class ImportFixerVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Check if this is importing from the old xml package
            String importName = importDecl.getNameAsString();
            if (importName.equals("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                // Replace with the correct import
                importDecl.setName("org.codehaus.plexus.util.Xpp3Dom");
                System.out.println("Fixed import: " + importName + " -> org.codehaus.plexus.util.Xpp3Dom");
            }
        }
    }
}
