package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic SnakeYAML 2.1 Breaking Change Fixer
 * 
 * This tool fixes the breaking change in SnakeYAML 2.1 where the inspector package
 * was removed and TrustedTagInspector was replaced with null or other alternatives.
 * 
 * Changes made:
 * 1. Removes imports from org.yaml.snakeyaml.inspector.*
 * 2. Replaces TrustedTagInspector usage with null
 * 
 * Usage: java -cp target/classes github.chains.Main /path/to/project
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -cp target/classes github.chains.Main <project-path>");
            System.exit(1);
        }
        
        String projectPath = args[0];
        try {
            processJavaFiles(projectPath);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFiles(String projectPath) throws IOException {
        Path basePath = Paths.get(projectPath);
        Files.walk(basePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Remove inspector package imports
            boolean modified = false;
            List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                    .filter(importDecl -> importDecl.getNameAsString().startsWith("org.yaml.snakeyaml.inspector"))
                    .collect(Collectors.toList());
            
            if (!importsToRemove.isEmpty()) {
                cu.getImports().removeAll(importsToRemove);
                modified = true;
            }
            
            // Replace TrustedTagInspector usages
            List<ObjectCreationExpr> creations = cu.findAll(ObjectCreationExpr.class);
            for (ObjectCreationExpr objectCreation : creations) {
                if (objectCreation.getType().getNameAsString().equals("TrustedTagInspector")) {
                    // Replace with null literal
                    objectCreation.replace(new NullLiteralExpr());
                    modified = true;
                }
            }
            
            // Save modified file if changes were made
            if (modified) {
                try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
                    writer.write(cu.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to process " + javaFilePath + ": " + e.getMessage());
        }
    }
}
