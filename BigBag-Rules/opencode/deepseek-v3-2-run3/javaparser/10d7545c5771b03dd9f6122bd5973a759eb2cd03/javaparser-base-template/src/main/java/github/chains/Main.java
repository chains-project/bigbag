package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: map of old package patterns to new package patterns
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Core Dropwizard package restructuring from version 2.x to 4.x
        // io.dropwizard.* -> io.dropwizard.core.* for core module classes
        PACKAGE_MAPPINGS.put("io.dropwizard.setup.", "io.dropwizard.core.setup.");
        PACKAGE_MAPPINGS.put("io.dropwizard.configuration.", "io.dropwizard.core.configuration.");
        PACKAGE_MAPPINGS.put("io.dropwizard.servlets.", "io.dropwizard.core.servlets.");
        PACKAGE_MAPPINGS.put("io.dropwizard.db.", "io.dropwizard.core.db.");
        PACKAGE_MAPPINGS.put("io.dropwizard.util.", "io.dropwizard.core.util.");
        PACKAGE_MAPPINGS.put("io.dropwizard.logging.", "io.dropwizard.core.logging.");
        PACKAGE_MAPPINGS.put("io.dropwizard.testing.", "io.dropwizard.core.testing.");
        PACKAGE_MAPPINGS.put("io.dropwizard.", "io.dropwizard.core.");
    }
    
    // Packages that should NOT be transformed (submodules)
    private static final Set<String> EXCLUDED_PACKAGES = new HashSet<>(Arrays.asList(
        "io.dropwizard.client.",
        "io.dropwizard.metrics.",
        "io.dropwizard.jdbi.",
        "io.dropwizard.jersey.",
        "io.dropwizard.jetty.",
        "io.dropwizard.jackson.",
        "io.dropwizard.validation.",
        "io.dropwizard.health.",
        "io.dropwizard.lifecycle.",
        "io.dropwizard.metrics.",
        "io.dropwizard.request.logging."
    ));
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Dropwizard package references in: " + sourceDir);
        
        try {
            transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File dir) throws Exception {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + dir.getPath());
        }
        
        List<File> javaFiles = Files.walk(dir.toPath())
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int transformedFiles = 0;
        
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile.getPath())
                );
                
                PackageTransformer transformer = new PackageTransformer();
                cu.accept(transformer, null);
                
                if (transformer.wasTransformed()) {
                    // Write back the transformed file
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    System.out.println("Transformed: " + javaFile.getPath());
                    transformedFiles++;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile.getPath() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Successfully transformed " + transformedFiles + " files");
    }
    
    private static class PackageTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import should be transformed
            for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                String oldPackage = entry.getKey();
                String newPackage = entry.getValue();
                
                if (importName.startsWith(oldPackage)) {
                    // Check if this package is excluded
                    boolean excluded = EXCLUDED_PACKAGES.stream()
                        .anyMatch(excludedPkg -> importName.startsWith(excludedPkg));
                    
                    if (!excluded) {
                        String newImportName = importName.replace(oldPackage, newPackage);
                        importDecl.setName(new Name(newImportName));
                        transformed = true;
                        System.out.println("  Import transformed: " + importName + " -> " + newImportName);
                        break;
                    }
                }
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Handle fully qualified type names in code (e.g., in method parameters, extends/implements)
            // This is a simplified approach - in practice, we'd need to handle qualified names
            // and consider the context (imports already transformed)
            
            return (Node) super.visit(type, arg);
        }
    }
}