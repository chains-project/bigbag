package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.QualifiedName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic JavaParser transformation rule to migrate Jakarta MVC API from javax.mvc to jakarta.mvc
 * This transformation handles all package renames and type references consistently across a project.
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            // Process all Java files in the target directory
            Path sourceDir = Paths.get("/workspace/jakartaee-mvc-sample");
            if (args.length > 0) {
                sourceDir = Paths.get(args[0]);
            }
            
            System.out.println("Processing files in: " + sourceDir);
            
            // Find all Java files recursively
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation
            MigrationTransformer transformer = new MigrationTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transformer that handles migration from javax.mvc to jakarta.mvc
     */
    private static class MigrationTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ImportDeclaration importDecl, Void arg) {
            // Transform import declarations
            Name name = importDecl.getName();
            if (name instanceof QualifiedName) {
                QualifiedName qualifiedName = (QualifiedName) name;
                String qualifiedNameStr = qualifiedName.getAsString();
                
                // Handle javax.mvc imports
                if (qualifiedNameStr.startsWith("javax.mvc.")) {
                    String jakartaPackage = qualifiedNameStr.replace("javax.mvc.", "jakarta.mvc.");
                    qualifiedName.setQualifier(new Name("jakarta"));
                    qualifiedName.setName("mvc");
                    
                    // Handle nested packages
                    String[] parts = qualifiedNameStr.split("\\.");
                    if (parts.length > 2) {
                        StringBuilder newPackage = new StringBuilder("jakarta.mvc");
                        for (int i = 2; i < parts.length; i++) {
                            newPackage.append(".").append(parts[i]);
                        }
                        qualifiedName.setQualifier(new Name("jakarta"));
                        qualifiedName.setName(newPackage.toString());
                    }
                }
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Visitable visit(QualifiedName qName, Void arg) {
            // Transform qualified names like javax.mvc.Controller to jakarta.mvc.Controller
            String qualifiedNameStr = qName.getAsString();
            
            // Handle javax.mvc package renames
            if (qualifiedNameStr.startsWith("javax.mvc.")) {
                String newPackageName = qualifiedNameStr.replace("javax.mvc.", "jakarta.mvc.");
                return new QualifiedName(
                    new Name("jakarta"),
                    new Name(newPackageName.substring(10)) // Remove "jakarta.mvc."
                );
            }
            
            return super.visit(qName, arg);
        }
    }
}
