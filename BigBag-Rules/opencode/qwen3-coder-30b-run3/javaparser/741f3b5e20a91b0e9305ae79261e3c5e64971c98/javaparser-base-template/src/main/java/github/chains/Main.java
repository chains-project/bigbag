package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            // Process all Java files recursively
            Files.walk(sourcePath)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path.toFile());
            
            // Apply transformations
            boolean modified = transformJacksonExceptions(cu);
            
            if (modified) {
                // Write back the modified file
                cu.save(path.toFile());
                System.out.println("Modified: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * Generic transformation for Jackson breaking changes:
     * - Replace com.fasterxml.jackson.core.exc.StreamReadException with 
     *   com.fasterxml.jackson.core.StreamReadException
     */
    private static boolean transformJacksonExceptions(CompilationUnit cu) {
        boolean modified = false;
        
        // Look for try-catch blocks that catch StreamReadException
        cu.findAll(TryStmt.class).forEach(tryStmt -> {
            tryStmt.getCatchClauses().forEach(catchClause -> {
                catchClause.getParameter().getType().ifClassOrInterfaceType(caughtType -> {
                    // Check if it's the old StreamReadException
                    if ("StreamReadException".equals(caughtType.getNameAsString()) &&
                        caughtType.getScope().isPresent() &&
                        "com.fasterxml.jackson.core.exc".equals(caughtType.getScope().get().asString())) {
                        
                        // Replace with the new StreamReadException
                        caughtType.setScope(com.github.javaparser.ast.expr.Name.valueOf("com.fasterxml.jackson.core"));
                        modified = true;
                    }
                });
            });
        });
        
        // Also look for imports that reference the old package
        cu.findAll(ImportDeclaration.class).forEach(importDecl -> {
            String importedName = importDecl.getNameAsString();
            if (importedName.equals("com.fasterxml.jackson.core.exc.StreamReadException")) {
                // Replace with the new import
                importDecl.setName("com.fasterxml.jackson.core.StreamReadException");
                modified = true;
            }
        });
        
        return modified;
    }
}
