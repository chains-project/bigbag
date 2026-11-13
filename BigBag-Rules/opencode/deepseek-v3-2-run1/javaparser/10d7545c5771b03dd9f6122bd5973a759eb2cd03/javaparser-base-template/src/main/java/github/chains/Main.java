package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    // Define package migration rules: old package -> new package
    private static final List<PackageMigration> PACKAGE_MIGRATIONS = Arrays.asList(
        // io.dropwizard.setup.* -> io.dropwizard.core.setup.*
        new PackageMigration("io.dropwizard.setup", "io.dropwizard.core.setup"),
        // io.dropwizard.Application -> io.dropwizard.core.Application  
        new PackageMigration("io.dropwizard", "io.dropwizard.core", "Application"),
        // io.dropwizard.configuration.* -> io.dropwizard.core.configuration.*
        new PackageMigration("io.dropwizard.configuration", "io.dropwizard.core.configuration"),
        // io.dropwizard.db.* -> io.dropwizard.core.db.*
        new PackageMigration("io.dropwizard.db", "io.dropwizard.core.db"),
        // io.dropwizard.logging.* -> io.dropwizard.core.logging.*
        new PackageMigration("io.dropwizard.logging", "io.dropwizard.core.logging"),
        // io.dropwizard.request.logging.* -> io.dropwizard.core.request.logging.*
        new PackageMigration("io.dropwizard.request.logging", "io.dropwizard.core.request.logging"),
        // io.dropwizard.servlets.tasks.* -> io.dropwizard.core.servlets.tasks.*
        new PackageMigration("io.dropwizard.servlets.tasks", "io.dropwizard.core.servlets.tasks"),
        // javax -> jakarta migration (Dropwizard 4.x uses Jakarta EE)
        new PackageMigration("javax.ws.rs", "jakarta.ws.rs"),
        new PackageMigration("javax.validation", "jakarta.validation"),
        new PackageMigration("javax.annotation", "jakarta.annotation"),
        new PackageMigration("javax.servlet", "jakarta.servlet"),
        new PackageMigration("javax.websocket", "jakarta.websocket")
        // io.dropwizard.testing.* -> io.dropwizard.testing.* (stays same for tests)
        // io.dropwizard.util.* -> io.dropwizard.util.* (stays same)
        // io.dropwizard.client.* -> io.dropwizard.client.* (stays same based on API doc)
        // Note: More migrations can be added as needed
    );
    
    static class PackageMigration {
        final String oldPackage;
        final String newPackage;
        final String specificClass; // null for all classes in package
        
        PackageMigration(String oldPackage, String newPackage) {
            this(oldPackage, newPackage, null);
        }
        
        PackageMigration(String oldPackage, String newPackage, String specificClass) {
            this.oldPackage = oldPackage;
            this.newPackage = newPackage;
            this.specificClass = specificClass;
        }
        
        boolean appliesTo(String fullyQualifiedName) {
            if (specificClass != null) {
                return fullyQualifiedName.equals(oldPackage + "." + specificClass) ||
                       fullyQualifiedName.startsWith(oldPackage + "." + specificClass + ".");
            } else {
                return fullyQualifiedName.startsWith(oldPackage + ".");
            }
        }
        
        String migrate(String fullyQualifiedName) {
            if (specificClass != null) {
                if (fullyQualifiedName.equals(oldPackage + "." + specificClass)) {
                    return newPackage + "." + specificClass;
                } else if (fullyQualifiedName.startsWith(oldPackage + "." + specificClass + ".")) {
                    return newPackage + "." + specificClass + fullyQualifiedName.substring((oldPackage + "." + specificClass).length());
                }
            } else {
                return newPackage + fullyQualifiedName.substring(oldPackage.length());
            }
            return fullyQualifiedName;
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Dropwizard imports from version 2.x/3.x to 4.x...");
        System.out.println("Source directory: " + sourceDir.toAbsolutePath());
        
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            javaFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        JavaParser javaParser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Warning: Could not parse " + javaFile);
                    continue;
                }
                
                boolean fileModified = false;
                
                // Transform imports
                List<ImportDeclaration> imports = cu.getImports();
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    String migratedName = migratePackage(importName);
                    
                    if (!importName.equals(migratedName)) {
                        importDecl.setName(migratedName);
                        fileModified = true;
                        totalTransformations++;
                        System.out.println("  Import: " + importName + " -> " + migratedName);
                    }
                }
                
                // Transform type references in the code
                List<ClassOrInterfaceType> typeRefs = cu.findAll(ClassOrInterfaceType.class);
                for (ClassOrInterfaceType typeRef : typeRefs) {
                    String typeName = typeRef.getNameAsString();
                    // For simple type names, we need to check if they match any migrated classes
                    // This is more complex and would require resolving imports
                    // For now, we focus on imports and fully-qualified names
                }
                
                // Also check for fully qualified names in the code
                cu.walk(Node.TreeTraversal.POSTORDER, node -> {
                    // Look for any fully qualified names that match our migration patterns
                    // This would require more sophisticated AST traversal
                    // For simplicity, we're focusing on import statements
                });
                
                if (fileModified) {
                    // Write the transformed file
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    config.setPrintComments(true);
                    config.setEndOfLineCharacter("\n");
                    
                    String transformedCode = cu.toString(config);
                    Files.write(javaFile, transformedCode.getBytes());
                    
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    private static String migratePackage(String fullyQualifiedName) {
        for (PackageMigration migration : PACKAGE_MIGRATIONS) {
            if (migration.appliesTo(fullyQualifiedName)) {
                return migration.migrate(fullyQualifiedName);
            }
        }
        return fullyQualifiedName;
    }
}