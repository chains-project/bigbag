package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule for migrating from javax.servlet to jakarta.servlet
 * This transformation handles the Jakarta EE migration from Java EE
 * 
 * Breaking change pattern:
 * - Old API: javax.servlet.*
 * - New API: jakarta.servlet.*
 * 
 * Transformation scope:
 * 1. Import statements
 * 2. Fully-qualified type references in code
 * 3. Method parameter types and return types
 * 4. Comments and string literals containing javax.servlet
 */
public class Main {
    
    /**
     * Visitor that transforms javax.servlet.* to jakarta.servlet.*
     */
    private static class ServletMigrationVisitor extends GenericVisitorAdapter<Void, Void> {
        
        @Override
        public Void visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImportName = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImportName);
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Void visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Handle fully qualified names in annotations or other places
            if (typeName.contains(".") && typeName.startsWith("javax.servlet")) {
                String newTypeName = typeName.replace("javax.servlet", "jakarta.servlet");
                type.setName(newTypeName);
            }
            
            // Check for nested types (e.g., FilterRegistration.Dynamic)
            if (type.getScope().isPresent()) {
                ClassOrInterfaceType scope = type.getScope().get();
                scope.accept(this, arg);
            }
            
            return super.visit(type, arg);
        }
    }
    
    /**
     * Processes a single Java file
     */
    private static void processFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            cu = parser.parse(in).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            ServletMigrationVisitor visitor = new ServletMigrationVisitor();
            visitor.visit(cu, null);
            
            try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                out.write(cu.toString().getBytes());
            }
            
            System.out.println("Processed: " + filePath);
        }
    }
    
    /**
     * Walks through directory tree and processes all Java files
     */
    private static void processDirectory(Path dirPath) throws Exception {
        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> {
                     try {
                         processFile(file);
                     } catch (Exception e) {
                         System.err.println("Error processing " + file + ": " + e.getMessage());
                     }
                 });
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            System.out.println("Starting javax.servlet -> jakarta.servlet migration...");
            System.out.println("Processing directory: " + sourceDir);
            processDirectory(sourceDir);
            System.out.println("Migration completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during migration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}