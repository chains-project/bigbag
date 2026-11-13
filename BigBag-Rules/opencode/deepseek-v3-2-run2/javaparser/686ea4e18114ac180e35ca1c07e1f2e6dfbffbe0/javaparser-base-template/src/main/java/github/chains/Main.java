package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for Dropwizard 4.0.0 breaking changes
 * This rule handles the migration from:
 * - javax.ws.rs.* -> jakarta.ws.rs.* (JAX-RS 2.x -> 3.x)
 * - javax.servlet.* -> jakarta.servlet.* (Servlet API)
 * - io.dropwizard.* -> io.dropwizard.core.* (Dropwizard package restructuring)
 * - io.dropwizard.logging.* -> io.dropwizard.logging.common.* (logging package restructuring)
 */
public class Main {
    
    private static final List<ImportReplacement> IMPORT_REPLACEMENTS = List.of(
        // JAX-RS migration: javax.ws.rs -> jakarta.ws.rs
        new ImportReplacement("javax.ws.rs", "jakarta.ws.rs"),
        new ImportReplacement("javax.ws.rs.client", "jakarta.ws.rs.client"),
        new ImportReplacement("javax.ws.rs.core", "jakarta.ws.rs.core"),
        new ImportReplacement("javax.ws.rs.container", "jakarta.ws.rs.container"),
        new ImportReplacement("javax.ws.rs.ext", "jakarta.ws.rs.ext"),
        
        // Servlet migration: javax.servlet -> jakarta.servlet
        new ImportReplacement("javax.servlet", "jakarta.servlet"),
        new ImportReplacement("javax.servlet.http", "jakarta.servlet.http"),
        
        // Dropwizard core package restructuring
        new ImportReplacement("io.dropwizard.Application", "io.dropwizard.core.Application"),
        new ImportReplacement("io.dropwizard.Configuration", "io.dropwizard.core.Configuration"),
        new ImportReplacement("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle"),
        new ImportReplacement("io.dropwizard.cli", "io.dropwizard.core.cli"),
        new ImportReplacement("io.dropwizard.server", "io.dropwizard.core.server"),
        new ImportReplacement("io.dropwizard.setup", "io.dropwizard.core.setup"),
        new ImportReplacement("io.dropwizard.sslreload", "io.dropwizard.core.sslreload"),
        new ImportReplacement("io.dropwizard.validation", "io.dropwizard.core.validation"),
        
        // Dropwizard logging package restructuring
        new ImportReplacement("io.dropwizard.logging.async", "io.dropwizard.logging.common.async"),
        new ImportReplacement("io.dropwizard.logging.filter", "io.dropwizard.logging.common.filter"),
        new ImportReplacement("io.dropwizard.logging.layout", "io.dropwizard.logging.common.layout"),
        
        // Jackson JAX-RS provider migration
        new ImportReplacement("com.fasterxml.jackson.jaxrs.json", "com.fasterxml.jackson.jakarta.rs.json"),
        new ImportReplacement("com.fasterxml.jackson.jaxrs.base", "com.fasterxml.jackson.jakarta.rs.base"),
        new ImportReplacement("com.fasterxml.jackson.jaxrs", "com.fasterxml.jackson.jakarta.rs"),
        
        // Fix duplicate common segments in logging
        new ImportReplacement("io.dropwizard.logging.common.common", "io.dropwizard.logging.common")
    );
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            var parseResult = parser.parse(content);
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse file " + filePath + ": " + parseResult.getProblems());
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().orElseThrow();
            boolean modified = false;
            
            // Apply import replacements
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                for (ImportReplacement replacement : IMPORT_REPLACEMENTS) {
                    if (importName.startsWith(replacement.oldPrefix)) {
                        String newImportName = importName.replace(replacement.oldPrefix, replacement.newPrefix);
                        importDecl.setName(new Name(newImportName));
                        modified = true;
                        System.out.println("Replaced import: " + importName + " -> " + newImportName);
                        break;
                    }
                }
            }
            
            // Apply type reference replacements in the AST
            TypeReferenceVisitor visitor = new TypeReferenceVisitor();
            cu.accept(visitor, null);
            if (visitor.isModified()) {
                modified = true;
            }
            
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Updated file: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that replaces type references in the AST
     */
    private static class TypeReferenceVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameStr = name.asString();
            
            // Check if this name matches any of our replacement patterns
            for (ImportReplacement replacement : IMPORT_REPLACEMENTS) {
                if (nameStr.startsWith(replacement.oldPrefix)) {
                    String newName = nameStr.replace(replacement.oldPrefix, replacement.newPrefix);
                    name.setIdentifier(newName);
                    modified = true;
                    System.out.println("Replaced type reference: " + nameStr + " -> " + newName);
                    break;
                }
            }
            
            return super.visit(name, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    /**
     * Represents an import replacement pattern
     */
    private static class ImportReplacement {
        final String oldPrefix;
        final String newPrefix;
        
        ImportReplacement(String oldPrefix, String newPrefix) {
            this.oldPrefix = oldPrefix;
            this.newPrefix = newPrefix;
        }
    }
}