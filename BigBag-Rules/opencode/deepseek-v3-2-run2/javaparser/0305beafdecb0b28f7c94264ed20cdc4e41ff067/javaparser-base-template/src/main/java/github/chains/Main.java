package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for MySQL connector package migration.
 * 
 * This transformation handles the breaking change where MySQLTimeoutException
 * moved from com.mysql.jdbc.exceptions to com.mysql.cj.jdbc.exceptions
 * in MySQL Connector/J 8.0.28.
 * 
 * The transformation is parameterized by:
 * - oldPackage: The old package pattern to match
 * - newPackage: The new package pattern to replace with
 * - className: The class name that moved packages
 */
public class Main {
    
    // Configuration parameters - these can be changed for different breaking changes
    private static final String OLD_PACKAGE = "com.mysql.jdbc.exceptions";
    private static final String NEW_PACKAGE = "com.mysql.cj.jdbc.exceptions";
    private static final String CLASS_NAME = "MySQLTimeoutException";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation will update:");
            System.err.println("  " + OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
        
        try {
            List<Path> modifiedFiles = processDirectory(Paths.get(sourceDir));
            
            if (modifiedFiles.isEmpty()) {
                System.out.println("No files were modified.");
            } else {
                System.out.println("Successfully modified " + modifiedFiles.size() + " files:");
                modifiedFiles.forEach(file -> System.out.println("  " + file));
            }
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Recursively processes all Java files in the directory and applies the transformation.
     */
    private static List<Path> processDirectory(Path directory) throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + directory);
        }
        
        List<Path> javaFiles = Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles.add(javaFile);
            }
        }
        
        return modifiedFiles;
    }
    
    /**
     * Processes a single Java file and applies the transformation if needed.
     * Returns true if the file was modified.
     */
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            if (isTargetImport(importDecl)) {
                System.out.println("Found target import in " + filePath + ": " + importDecl.getNameAsString());
                
                // Replace the import with the new package
                Name newName = StaticJavaParser.parseName(NEW_PACKAGE + "." + CLASS_NAME);
                importDecl.setName(newName);
                modified = true;
                
                System.out.println("  Updated to: " + importDecl.getNameAsString());
            }
        }
        
        if (modified) {
            // Write the modified content back to the file
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
            String newContent = printer.print(cu);
            Files.writeString(filePath, newContent);
            System.out.println("  Successfully updated " + filePath);
        }
        
        return modified;
    }
    
    /**
     * Checks if an import declaration matches the target pattern we want to transform.
     */
    private static boolean isTargetImport(ImportDeclaration importDecl) {
        String importName = importDecl.getNameAsString();
        
        // Match imports of the specific class from the old package
        boolean isExactMatch = importName.equals(OLD_PACKAGE + "." + CLASS_NAME);
        
        // Also match wildcard imports of the old package
        boolean isWildcardMatch = importDecl.isAsterisk() && 
                                 importName.equals(OLD_PACKAGE);
        
        return isExactMatch || isWildcardMatch;
    }
}