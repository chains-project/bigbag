package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JavaParser transformation to fix breaking changes in JavaParser 3.24.0:
 * - Removes references to the deprecated PrettyPrinterConfiguration class
 * - Replaces usage of new PrettyPrinterConfiguration().setPrintComments(false) 
 *   with an equivalent DefaultPrinterConfiguration approach
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(new File(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            throw new IOException("Directory does not exist: " + directory.getAbsolutePath());
        }
        
        Files.walk(directory.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            SourceRoot sourceRoot = new SourceRoot(filePath.getParent());
            CompilationUnit cu = sourceRoot.parse("", filePath.getFileName().toString());
            
            // Find and fix PrettyPrinterConfiguration usage
            boolean modified = fixPrettyPrinterConfiguration(cu);
            
            if (modified) {
                // Save the modified file
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Fix usage of PrettyPrinterConfiguration that was removed in JavaParser 3.24.0
     * Specifically looks for patterns like:
     *   new PrettyPrinterConfiguration().setPrintComments(false)
     * and replaces with equivalent DefaultPrinterConfiguration usage
     */
    private static boolean fixPrettyPrinterConfiguration(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all object creation expressions for PrettyPrinterConfiguration
        List<ObjectCreationExpr> creations = new ArrayList<>();
        cu.findAll(ObjectCreationExpr.class).forEach(objectCreation -> {
            if (objectCreation.getType().getNameAsString().equals("PrettyPrinterConfiguration")) {
                creations.add(objectCreation);
            }
        });
        
        // Process found creations
        for (ObjectCreationExpr objectCreation : creations) {
            // Replace PrettyPrinterConfiguration with DefaultPrinterConfiguration
            objectCreation.setType(new ClassOrInterfaceType("DefaultPrinterConfiguration"));
            modified = true;
        }
        
        // Find all field declarations with PrettyPrinterConfiguration type
        List<FieldDeclaration> fieldsToRemove = new ArrayList<>();
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.getElementType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) field.getElementType();
                if ("PrettyPrinterConfiguration".equals(type.getNameAsString())) {
                    fieldsToRemove.add(field);
                }
            }
        });
        
        // Process found fields
        for (FieldDeclaration field : fieldsToRemove) {
            // Change the type to DefaultPrinterConfiguration
            field.setElementType(new ClassOrInterfaceType("DefaultPrinterConfiguration"));
            modified = true;
        }
        
        // Remove import statement for PrettyPrinterConfiguration
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        cu.findAll(ImportDeclaration.class).forEach(importDecl -> {
            if (importDecl.getNameAsString().equals("com.github.javaparser.printer.PrettyPrinterConfiguration")) {
                importsToRemove.add(importDecl);
            }
        });
        
        for (ImportDeclaration importDecl : importsToRemove) {
            importDecl.remove();
            modified = true;
        }
        
        return modified;
    }
}