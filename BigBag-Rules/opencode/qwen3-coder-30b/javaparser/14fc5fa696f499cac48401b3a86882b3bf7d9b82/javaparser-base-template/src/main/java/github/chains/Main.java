package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.apache.commons.io.FileUtils;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        File sourceDir = new File(args[0]);
        List<File> javaFiles = listJavaFiles(sourceDir);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (File file : javaFiles) {
            if (fixFlywayConstructor(file)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static List<File> listJavaFiles(File dir) {
        return new ArrayList<>(FileUtils.listFiles(dir, new String[]{"java"}, true));
    }
    
    private static boolean fixFlywayConstructor(File file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        final boolean[] modified = {false};
        
        // Check if we need to add the import for FluentConfiguration
        boolean hasFluentConfigurationImport = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.flywaydb.core.api.configuration.FluentConfiguration")) {
                hasFluentConfigurationImport = true;
                break;
            }
        }
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a Flyway object creation with no arguments
                if (n.getType() != null && n.getType().getNameAsString().equals("Flyway")) {
                    // Check if it's in the org.flywaydb.core package or just Flyway
                    if (n.getType().getScope().isPresent()) {
                        if (n.getType().getScope().get().getNameAsString().equals("org.flywaydb.core")) {
                            // Check if it has no arguments (no-argument constructor)
                            if (n.getArguments().isEmpty()) {
                                // Replace with FluentConfiguration.configure()
                                MethodCallExpr fluentConfig = new MethodCallExpr(
                                    new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"), 
                                    "configure"
                                );
                                
                                ObjectCreationExpr newCreation = new ObjectCreationExpr(
                                    null, 
                                    new ClassOrInterfaceType(null, "Flyway"), 
                                    new NodeList<>(fluentConfig)
                                );
                                
                                n.replace(newCreation);
                                modified[0] = true;
                            }
                        }
                    } else {
                        // No scope means just "Flyway", check if it's the right one
                        // Check if it's a no-argument constructor
                        if (n.getArguments().isEmpty()) {
                            // Replace with FluentConfiguration.configure()
                            MethodCallExpr fluentConfig = new MethodCallExpr(
                                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"), 
                                "configure"
                            );
                            
                            ObjectCreationExpr newCreation = new ObjectCreationExpr(
                                null, 
                                new ClassOrInterfaceType(null, "Flyway"), 
                                new NodeList<>(fluentConfig)
                            );
                            
                            n.replace(newCreation);
                            modified[0] = true;
                        }
                    }
                }
            }
        }, null);
        
        // Add the import if needed
        if (modified[0] && !hasFluentConfigurationImport) {
            ImportDeclaration importDecl = new ImportDeclaration(
                new com.github.javaparser.ast.expr.Name("org.flywaydb.core.api.configuration.FluentConfiguration"), 
                false, 
                false
            );
            cu.addImport(importDecl);
        }
        
        // Write the changes back to the file if modified
        if (modified[0]) {
            java.nio.file.Files.write(file.toPath(), cu.toString().getBytes());
        }
        
        return modified[0];
    }
}
        
        File sourceDir = new File(args[0]);
        List<File> javaFiles = listJavaFiles(sourceDir);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (File file : javaFiles) {
            if (fixFlywayConstructor(file)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static List<File> listJavaFiles(File dir) {
        return new ArrayList<>(FileUtils.listFiles(dir, new String[]{"java"}, true));
    }
    
    private static boolean fixFlywayConstructor(File file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean modified = false;
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a Flyway object creation with no arguments
                if (n.getType() != null && n.getType().getNameAsString().equals("Flyway")) {
                    // Check if it's in the org.flywaydb.core package or just Flyway
                    if (n.getType().getScope().isPresent()) {
                        if (n.getType().getScope().get().getNameAsString().equals("org.flywaydb.core")) {
                            // Check if it has no arguments (no-argument constructor)
                            if (n.getArguments().isEmpty()) {
                                // Replace with FluentConfiguration.configure()
                                MethodCallExpr fluentConfig = new MethodCallExpr(
                                    new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"), 
                                    "configure"
                                );
                                
                                ObjectCreationExpr newCreation = new ObjectCreationExpr(
                                    null, 
                                    new ClassOrInterfaceType(null, "Flyway"), 
                                    new com.github.javaparser.ast.NodeList<>(fluentConfig)
                                );
                                
                                n.replace(newCreation);
                                modified = true;
                            }
                        }
                    } else {
                        // No scope means just "Flyway", check if it's the right one
                        // Check if it's a no-argument constructor
                        if (n.getArguments().isEmpty()) {
                            // Replace with FluentConfiguration.configure()
                            MethodCallExpr fluentConfig = new MethodCallExpr(
                                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"), 
                                "configure"
                            );
                            
                            ObjectCreationExpr newCreation = new ObjectCreationExpr(
                                null, 
                                new ClassOrInterfaceType(null, "Flyway"), 
                                new com.github.javaparser.ast.NodeList<>(fluentConfig)
                            );
                            
                            n.replace(newCreation);
                            modified = true;
                        }
                    }
                }
            }
        }, null);
        
        // Write the changes back to the file if modified
        if (modified) {
            java.nio.file.Files.write(file.toPath(), cu.toString().getBytes());
        }
        
        return modified;
    }
}
