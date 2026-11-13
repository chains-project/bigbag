package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for libthrift 0.16.0 breaking change:
 * TFramedTransport moved from org.apache.thrift.transport to org.apache.thrift.transport.layered
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_PACKAGE = "org.apache.thrift.transport.TFramedTransport";
    private static final String NEW_PACKAGE = "org.apache.thrift.transport.layered.TFramedTransport";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            
            // Check if file contains TFramedTransport references
            if (!content.contains("TFramedTransport")) {
                return false;
            }
            
            System.out.println("Processing file: " + filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            TFramedTransportTransformer transformer = new TFramedTransportTransformer();
            CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
            
            // Check if imports were actually changed
            boolean hasOldImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("org.apache.thrift.transport.TFramedTransport"));
            boolean hasNewImport = transformedCu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("org.apache.thrift.transport.layered.TFramedTransport"));
            
            if (hasNewImport && !hasOldImport) {
                // File was modified, write it back
                Files.writeString(filePath, transformedCu.toString());
                System.out.println("Modified: " + filePath);
                return true;
            } else {
                System.out.println("No changes needed for: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Visitor that transforms TFramedTransport imports and references
     */
    private static class TFramedTransportTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this is the old TFramedTransport import
            if (importName.equals(OLD_PACKAGE) || importName.equals("org.apache.thrift.transport.TFramedTransport")) {
                System.out.println("Found old TFramedTransport import: " + importName);
                // Replace with new package
                ImportDeclaration newImport = importDecl.clone();
                newImport.setName(NEW_PACKAGE);
                return newImport;
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameAsString = name.asString();
            
            // Check if this is a reference to the old TFramedTransport class
            if (nameAsString.equals(OLD_PACKAGE)) {
                // Replace with new package
                return new Name(NEW_PACKAGE);
            }
            
            // Check for simple name references (when imported)
            if (nameAsString.equals("TFramedTransport")) {
                // We need to check if this is actually referring to the TFramedTransport class
                // This is a simplified approach - in a real scenario you'd want more context
                return name;
            }
            
            return super.visit(name, arg);
        }
    }
}