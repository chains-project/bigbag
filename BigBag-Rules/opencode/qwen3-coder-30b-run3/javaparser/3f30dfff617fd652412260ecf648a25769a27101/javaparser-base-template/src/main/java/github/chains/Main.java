package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for Jakarta Interceptor API 2.0.0 breaking changes.
 * This tool identifies common breaking changes when migrating from Jakarta Interceptor API 1.x to 2.0.0,
 * particularly focusing on package migrations in import statements.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            System.err.println("Invalid directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            processDirectory(dirPath);
            System.out.println("Analysis completed. Check output for Jakarta Interceptor API issues.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dirPath) throws IOException {
        Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Report any javax.interceptor imports that need to be changed
            checkImports(cu, filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Checks for imports from javax.interceptor that need to be changed
     */
    private static void checkImports(CompilationUnit cu, Path filePath) {
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith("javax.interceptor")) {
                System.out.println("Found javax.interceptor import in " + filePath + ": " + importName);
                System.out.println("  Should be changed to: " + importName.replace("javax.interceptor", "jakarta.interceptor"));
            }
        }
    }
}