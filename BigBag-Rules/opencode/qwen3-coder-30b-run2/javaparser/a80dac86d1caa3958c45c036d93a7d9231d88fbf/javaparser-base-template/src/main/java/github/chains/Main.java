package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Generic JavaParser transformation for fixing breaking dependency updates.
 * This transformation addresses cases where API elements have been removed
 * in newer dependency versions.
 */
public class Main {
    
    private static final List<String> REMOVED_TYPES = Arrays.asList(
        "org.slf4j.spi.LoggingEventAware"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing directory: " + sourceDirectory);
        
        try {
            // Parse all Java files in the directory
            Path dirPath = Paths.get(sourceDirectory);
            Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processFile(path);
                    } catch (Exception e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a single Java file to identify and fix breaking changes
     */
    private static void processFile(Path filePath) throws Exception {
        String code = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(code);
        
        boolean modified = processCompilationUnit(cu);
        
        if (modified) {
            // Write back the modified file
            String newCode = LexicalPreservingPrinter.print(cu);
            Files.write(filePath, newCode.getBytes());
            System.out.println("Modified file: " + filePath);
        }
    }
    
    /**
     * Process a compilation unit to identify and fix breaking changes
     */
    private static boolean processCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Remove imports of removed types
        modified |= removeRemovedImports(cu);
        
        return modified;
    }
    
    /**
     * Remove import statements for removed types
     */
    private static boolean removeRemovedImports(CompilationUnit cu) {
        boolean modified = false;
        for (String removedType : REMOVED_TYPES) {
            // Remove import statements
            Optional<com.github.javaparser.ast.ImportDeclaration> importToRemove = 
                cu.getImports().stream()
                  .filter(importDecl -> importDecl.getNameAsString().equals(removedType))
                  .findFirst();
                  
            if (importToRemove.isPresent()) {
                cu.remove(importToRemove.get());
                modified = true;
            }
        }
        return modified;
    }
}