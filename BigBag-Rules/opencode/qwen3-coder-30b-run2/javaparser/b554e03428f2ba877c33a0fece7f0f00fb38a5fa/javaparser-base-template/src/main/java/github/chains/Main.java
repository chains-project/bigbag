package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the provided source directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/license-maven-plugin/license-maven-plugin/src/main/java";
        
        // Process all Java files recursively
        Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Remove the problematic import
            NodeList<ImportDeclaration> imports = cu.getImports();
            ImportDeclaration toRemove = null;
            for (ImportDeclaration imp : imports) {
                if (imp.getNameAsString().contains("Maven31DependencyGraphBuilder")) {
                    toRemove = imp;
                    break;
                }
            }
            if (toRemove != null) {
                imports.remove(toRemove);
            }
            
            // Add the correct import if needed
            boolean hasDefaultGraphBuilderImport = false;
            for (ImportDeclaration imp : imports) {
                if (imp.getNameAsString().contains("DefaultDependencyGraphBuilder")) {
                    hasDefaultGraphBuilderImport = true;
                    break;
                }
            }
            
            if (!hasDefaultGraphBuilderImport) {
                // Add the correct import
                ImportDeclaration newImport = new ImportDeclaration("org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder", false, false);
                imports.add(newImport);
            }
            
            // Write the modified content back to the file
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}