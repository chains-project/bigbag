package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // List of Dropwizard core classes that moved from io.dropwizard.* to io.dropwizard.core.*
    private static final List<String> CORE_DROPWIZARD_CLASSES = List.of(
        "Configuration",
        "Application",
        "ConfiguredBundle",
        "cli.CheckCommand",
        "cli.Cli",
        "cli.ServerCommand",
        "server.DefaultServerFactory",
        "server.SimpleServerFactory",
        "setup.AdminEnvironment",
        "setup.AdminFactory",
        "setup.Bootstrap",
        "setup.Environment",
        "setup.ExceptionMapperBinder",
        "setup.HealthCheckConfiguration",
        "sslreload.SslReloadBundle",
        "sslreload.SslReloadTask",
        "validation.InjectValidatorFeature"
    );
    
    // List of packages that moved from io.dropwizard.* to io.dropwizard.core.*
    private static final List<String> CORE_DROPWIZARD_PACKAGES = List.of(
        "io.dropwizard.setup.",
        "io.dropwizard.util.",
        "io.dropwizard.cli.",
        "io.dropwizard.server.",
        "io.dropwizard.sslreload.",
        "io.dropwizard.validation.",
        "io.dropwizard.health.",
        "io.dropwizard.testing."
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            JavaParser javaParser = new JavaParser();
            int modifiedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                try (FileInputStream fis = new FileInputStream(javaFile.toFile())) {
                    CompilationUnit cu = javaParser.parse(fis).getResult().orElse(null);
                    
                    if (cu != null) {
                        boolean wasModified = false;
                        NodeList<ImportDeclaration> imports = cu.getImports();
                        
                        // Create a list to hold new imports
                        List<ImportDeclaration> newImports = new ArrayList<>();
                        
                        for (ImportDeclaration importDecl : imports) {
                            String importName = importDecl.getNameAsString();
                            ImportDeclaration newImport = importDecl;
                            
                            // 1. Transform javax.ws.rs imports to jakarta.ws.rs
                            if (importName.startsWith("javax.ws.rs")) {
                                String newImportName = importName.replace("javax.ws.rs", "jakarta.ws.rs");
                                newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                wasModified = true;
                                System.out.println("  Updated JAX-RS import: " + importName + " -> " + newImportName);
                            }
                            
                            // 2. Transform javax.servlet imports to jakarta.servlet
                            else if (importName.startsWith("javax.servlet")) {
                                String newImportName = importName.replace("javax.servlet", "jakarta.servlet");
                                newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                wasModified = true;
                                System.out.println("  Updated Servlet import: " + importName + " -> " + newImportName);
                            }
                            
                            // 3. Revert jakarta.annotation back to javax.annotation (not migrated)
                            else if (importName.startsWith("jakarta.annotation")) {
                                String newImportName = importName.replace("jakarta.annotation", "javax.annotation");
                                newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                wasModified = true;
                                System.out.println("  Reverted annotation import: " + importName + " -> " + newImportName);
                            }
                            
                            // 4. Transform io.dropwizard.logging.* to io.dropwizard.logging.common.*
                            else if (importName.startsWith("io.dropwizard.logging.") && 
                                     !importName.startsWith("io.dropwizard.logging.common.")) {
                                String newImportName = importName.replace("io.dropwizard.logging.", "io.dropwizard.logging.common.");
                                newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                wasModified = true;
                                System.out.println("  Updated logging import: " + importName + " -> " + newImportName);
                            }
                            
                            // 5. Transform core Dropwizard classes and packages
                            else if (importName.startsWith("io.dropwizard.")) {
                                boolean isCoreClass = CORE_DROPWIZARD_CLASSES.stream()
                                    .anyMatch(cls -> importName.equals("io.dropwizard." + cls));
                                boolean isCorePackage = CORE_DROPWIZARD_PACKAGES.stream()
                                    .anyMatch(pkg -> importName.startsWith(pkg));
                                    
                                if (isCoreClass || isCorePackage) {
                                    String newImportName = importName.replace("io.dropwizard.", "io.dropwizard.core.");
                                    newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                    wasModified = true;
                                    System.out.println("  Updated core Dropwizard import: " + importName + " -> " + newImportName);
                                }
                            }
                            
                            // 6. Transform com.fasterxml.jackson.jaxrs.json to com.fasterxml.jackson.jakarta.rs.json
                            else if (importName.startsWith("com.fasterxml.jackson.jaxrs.json")) {
                                String newImportName = importName.replace("com.fasterxml.jackson.jaxrs.json", "com.fasterxml.jackson.jakarta.rs.json");
                                newImport = new ImportDeclaration(newImportName, importDecl.isStatic(), importDecl.isAsterisk());
                                wasModified = true;
                                System.out.println("  Updated Jackson import: " + importName + " -> " + newImportName);
                            }
                            
                            newImports.add(newImport);
                        }
                        
                        if (wasModified) {
                            // Replace all imports with updated ones
                            cu.setImports(new NodeList<>(newImports));
                            
                            // Also update fully qualified names in code
                            cu.accept(new ModifierVisitor<Void>() {
                                @Override
                                public Visitable visit(com.github.javaparser.ast.expr.NameExpr n, Void arg) {
                                    String name = n.getNameAsString();
                                    
                                    // Apply same transformations as for imports
                                    if (name.startsWith("javax.ws.rs")) {
                                        String newName = name.replace("javax.ws.rs", "jakarta.ws.rs");
                                        return new com.github.javaparser.ast.expr.NameExpr(newName);
                                    }
                                    else if (name.startsWith("javax.servlet")) {
                                        String newName = name.replace("javax.servlet", "jakarta.servlet");
                                        return new com.github.javaparser.ast.expr.NameExpr(newName);
                                    }
                                    else if (name.startsWith("jakarta.annotation")) {
                                        String newName = name.replace("jakarta.annotation", "javax.annotation");
                                        return new com.github.javaparser.ast.expr.NameExpr(newName);
                                    }
                                    else if (name.startsWith("io.dropwizard.logging.") && 
                                             !name.startsWith("io.dropwizard.logging.common.")) {
                                        String newName = name.replace("io.dropwizard.logging.", "io.dropwizard.logging.common.");
                                        return new com.github.javaparser.ast.expr.NameExpr(newName);
                                    }
                                    else if (name.startsWith("io.dropwizard.")) {
                                        boolean isCoreClass = CORE_DROPWIZARD_CLASSES.stream()
                                            .anyMatch(cls -> name.equals("io.dropwizard." + cls));
                                        boolean isCorePackage = CORE_DROPWIZARD_PACKAGES.stream()
                                            .anyMatch(pkg -> name.startsWith(pkg));
                                            
                                        if (isCoreClass || isCorePackage) {
                                            String newName = name.replace("io.dropwizard.", "io.dropwizard.core.");
                                            return new com.github.javaparser.ast.expr.NameExpr(newName);
                                        }
                                    }
                                    else if (name.startsWith("com.fasterxml.jackson.jaxrs.json")) {
                                        String newName = name.replace("com.fasterxml.jackson.jaxrs.json", "com.fasterxml.jackson.jakarta.rs.json");
                                        return new com.github.javaparser.ast.expr.NameExpr(newName);
                                    }
                                    
                                    return super.visit(n, arg);
                                }
                                
                                @Override
                                public Visitable visit(com.github.javaparser.ast.type.ClassOrInterfaceType n, Void arg) {
                                    // Handle class extends and implements clauses
                                    if (n.getScope().isPresent()) {
                                        String qualifiedName = n.getScope().get().asString() + "." + n.getNameAsString();
                                        
                                        // Check if it's a Dropwizard core class
                                        if (qualifiedName.equals("io.dropwizard.Configuration") || 
                                            qualifiedName.equals("io.dropwizard.Application")) {
                                            String newName = qualifiedName.replace("io.dropwizard.", "io.dropwizard.core.");
                                            return new com.github.javaparser.ast.type.ClassOrInterfaceType(newName);
                                        }
                                    }
                                    
                                    return super.visit(n, arg);
                                }
                            }, null);
                            
                            // Write the modified file
                            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                                writer.write(cu.toString());
                            }
                            
                            modifiedFiles++;
                            System.out.println("  Modified: " + javaFile);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("\nTotal files modified: " + modifiedFiles + " out of " + javaFiles.size());
            
            // Print summary of changes
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("1. javax.ws.rs.* -> jakarta.ws.rs.*");
            System.out.println("2. javax.servlet.* -> jakarta.servlet.*");
            System.out.println("3. jakarta.annotation.* -> javax.annotation.* (reverted - not migrated)");
            System.out.println("4. io.dropwizard.logging.* -> io.dropwizard.logging.common.*");
            System.out.println("5. io.dropwizard.core classes moved to io.dropwizard.core.*");
            System.out.println("6. com.fasterxml.jackson.jaxrs.json -> com.fasterxml.jackson.jakarta.rs.json");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}