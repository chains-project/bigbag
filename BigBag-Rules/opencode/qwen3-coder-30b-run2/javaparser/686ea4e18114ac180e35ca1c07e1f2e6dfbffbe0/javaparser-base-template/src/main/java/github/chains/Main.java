package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for DropWizard 4.0.0 compatibility.
 * This transformation removes javax.ws.rs imports which were removed in DropWizard 4.0.0
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Find all Java files
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Remove javax.ws.rs imports
            boolean modified = removeJavaxWsRsImports(cu);
            
            if (modified) {
                // Save the modified file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Modified file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Remove imports of javax.ws.rs packages
     */
    private static boolean removeJavaxWsRsImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        // Remove all javax.ws.rs imports
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration imp = imports.get(i);
            if (imp.getNameAsString().startsWith("javax.ws.rs")) {
                imports.remove(i);
                modified = true;
            }
        }
        
        return modified;
    }
}