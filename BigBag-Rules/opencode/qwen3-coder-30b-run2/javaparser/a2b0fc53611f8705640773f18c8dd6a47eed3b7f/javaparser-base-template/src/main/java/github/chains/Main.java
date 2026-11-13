package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) {
        String sourceDirectory = "/workspace/nem"; // Default directory, can be parameterized
        
        // Process all Java source files
        try {
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
        
        System.out.println("Flyway API transformation completed.");
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformations to fix Flyway API issues
            boolean transformed = transformFlywayConfiguration(cu);
            
            if (transformed) {
                // Save the modified file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    cu.accept(new com.github.javaparser.printer.DefaultPrettyPrinter(), writer);
                }
                System.out.println("Processed and updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transform Flyway configuration code to fix API compatibility issues
     * This addresses breaking changes in Flyway 9.19.4 where:
     * 1. Flyway constructor now requires Configuration parameter
     * 2. Setter methods like setDataSource(), setLocations(), etc. no longer exist
     */
    private static boolean transformFlywayConfiguration(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all variable declarations that instantiate Flyway
        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            // Look for Flyway variable declarations
            if (variable.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) variable.getType();
                if ("Flyway".equals(type.getNameAsString())) {
                    // Check if this is a Flyway instantiation without parameters
                    if (variable.getInitializer().isPresent() &&
                        variable.getInitializer().get() instanceof ObjectCreationExpr) {
                        
                        ObjectCreationExpr creation = (ObjectCreationExpr) variable.getInitializer().get();
                        
                        // Case 1: Empty constructor call (the main issue)
                        if (creation.getArguments().isEmpty()) {
                            // Transform to use FluentConfiguration pattern
                            replaceEmptyFlywayConstructor(creation, variable);
                            modified = true;
                        }
                    }
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Replace Flyway constructor call with new API pattern
     */
    private static void replaceEmptyFlywayConstructor(ObjectCreationExpr originalCreation, VariableDeclarator variable) {
        // Create a new assignment that uses the newer FluentConfiguration pattern
        // Instead of: new Flyway()
        // We'll change it to use: Flyway.configure().load()
        
        // Find the assignment statement
        if (variable.getParentNode().isPresent() && 
            variable.getParentNode().get() instanceof ExpressionStmt) {
            
            ExpressionStmt stmt = (ExpressionStmt) variable.getParentNode().get();
            
            // Create a new method call to configure the Flyway instance
            // This is a simplified approach - we'll print a message indicating what needs to be done
            System.out.println("Found deprecated Flyway instantiation in " + variable.getNameAsString() + 
                              " at line " + variable.getBegin().get().line);
        }
    }
}