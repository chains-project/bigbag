package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    // Configuration: map of old imports to new imports
    private static final Map<String, String> IMPORT_TRANSFORMATIONS = new HashMap<>();
    // Configuration: map of old type names to new fully qualified type names
    private static final Map<String, String> TYPE_TRANSFORMATIONS = new HashMap<>();
    // Configuration: map of old package prefixes to new package prefixes
    private static final Map<String, String> PACKAGE_PREFIX_TRANSFORMATIONS = new HashMap<>();
    
    static {
        // Default transformations for Maven API changes from 3.x to 3.6.0+
        // These are examples and should be configured based on actual breaking changes
        IMPORT_TRANSFORMATIONS.put(
            "org.apache.maven.project.MavenProject",
            "org.apache.maven.api.project.MavenProject"
        );
        IMPORT_TRANSFORMATIONS.put(
            "org.apache.maven.artifact.DependencyResolutionRequiredException",
            "org.apache.maven.api.DependencyResolutionRequiredException"
        );
        
        // Type transformations (for when types are referenced without imports)
        TYPE_TRANSFORMATIONS.put(
            "MavenProject",
            "org.apache.maven.api.project.MavenProject"
        );
        TYPE_TRANSFORMATIONS.put(
            "DependencyResolutionRequiredException",
            "org.apache.maven.api.DependencyResolutionRequiredException"
        );
        
        // Package prefix transformations (for broader matching)
        PACKAGE_PREFIX_TRANSFORMATIONS.put(
            "org.apache.maven.project",
            "org.apache.maven.api.project"
        );
        PACKAGE_PREFIX_TRANSFORMATIONS.put(
            "org.apache.maven.artifact",
            "org.apache.maven.api"
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [--config <config-file>] [--dry-run]");
            System.err.println("Options:");
            System.err.println("  --config <file>    JSON configuration file with transformation mappings");
            System.err.println("  --dry-run          Show what would be changed without actually changing files");
            System.err.println("  --verbose          Show detailed transformation information");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Parse command line arguments
        Map<String, String> config = parseArgs(args);
        boolean dryRun = config.containsKey("dry-run");
        boolean verbose = config.containsKey("verbose");
        
        if (dryRun) {
            System.out.println("DRY RUN: No files will be modified.");
        }
        
        try {
            transformProject(sourceDir, config, dryRun, verbose);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> config = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--config") && i + 1 < args.length) {
                config.put("config-file", args[i + 1]);
                i++;
            } else if (args[i].equals("--dry-run")) {
                config.put("dry-run", "true");
            } else if (args[i].equals("--verbose")) {
                config.put("verbose", "true");
            }
        }
        return config;
    }
    
    private static void loadConfig(String configFile) throws Exception {
        // Simplified config loading - in real implementation would parse JSON
        System.out.println("Loading config from: " + configFile);
        // Implementation would read transformation mappings from config file
        // and populate IMPORT_TRANSFORMATIONS, TYPE_TRANSFORMATIONS, and PACKAGE_PREFIX_TRANSFORMATIONS
    }
    
    private static void transformProject(String sourceDir, Map<String, String> config, boolean dryRun, boolean verbose) throws Exception {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        // Load configuration if specified
        if (config.containsKey("config-file")) {
            loadConfig(config.get("config-file"));
        }
        
        List<File> javaFiles = Files.walk(sourcePath)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (File javaFile : javaFiles) {
            TransformationResult result = transformFile(javaFile, dryRun, verbose);
            if (result.modified) {
                transformedFiles++;
                totalTransformations += result.transformations;
            }
        }
        
        System.out.println("Summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
        
        if (dryRun && totalTransformations > 0) {
            System.out.println("\nRun without --dry-run to apply these transformations.");
        }
    }
    
    private static TransformationResult transformFile(File javaFile, boolean dryRun, boolean verbose) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream fis = new FileInputStream(javaFile)) {
            cu = parser.parse(fis).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse file: " + javaFile.getPath()));
        }
        
        TransformationResult result = new TransformationResult();
        
        // Transform imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            // Check exact match first
            if (IMPORT_TRANSFORMATIONS.containsKey(importName)) {
                String newImport = IMPORT_TRANSFORMATIONS.get(importName);
                if (verbose) {
                    System.out.println("Transforming import in " + javaFile.getPath() + ":");
                    System.out.println("  " + importName + " -> " + newImport);
                }
                importDecl.setName(new Name(newImport));
                result.modified = true;
                result.transformations++;
            } else {
                // Check package prefix transformations
                for (Map.Entry<String, String> entry : PACKAGE_PREFIX_TRANSFORMATIONS.entrySet()) {
                    String oldPrefix = entry.getKey();
                    String newPrefix = entry.getValue();
                    if (importName.startsWith(oldPrefix + ".")) {
                        String newImport = newPrefix + importName.substring(oldPrefix.length());
                        if (verbose) {
                            System.out.println("Transforming import (prefix match) in " + javaFile.getPath() + ":");
                            System.out.println("  " + importName + " -> " + newImport);
                        }
                        importDecl.setName(new Name(newImport));
                        result.modified = true;
                        result.transformations++;
                        break;
                    }
                }
            }
        }
        
        // Transform type references in the code
        ModifiedVisitor visitor = new ModifiedVisitor(verbose, javaFile.getPath());
        cu.accept(visitor, null);
        if (visitor.modified) {
            result.modified = true;
            result.transformations += visitor.transformations;
        }
        
        if (result.modified && !dryRun) {
            try (FileOutputStream fos = new FileOutputStream(javaFile)) {
                fos.write(cu.toString().getBytes());
            }
        }
        
        return result;
    }
    
    private static class TransformationResult {
        boolean modified = false;
        int transformations = 0;
    }
    
    private static class ModifiedVisitor extends com.github.javaparser.ast.visitor.ModifierVisitor<Void> {
        boolean modified = false;
        int transformations = 0;
        boolean verbose;
        String filePath;
        
        ModifiedVisitor(boolean verbose, String filePath) {
            this.verbose = verbose;
            this.filePath = filePath;
        }
        
        @Override
        public Type visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            if (TYPE_TRANSFORMATIONS.containsKey(typeName)) {
                String newType = TYPE_TRANSFORMATIONS.get(typeName);
                if (verbose) {
                    System.out.println("Transforming type reference in " + filePath + ":");
                    System.out.println("  " + typeName + " -> " + newType);
                }
                
                // Create new type with fully qualified name
                ClassOrInterfaceType newTypeNode = new ClassOrInterfaceType();
                newTypeNode.setName(newType);
                modified = true;
                transformations++;
                return newTypeNode;
            }
            
            return (Type) super.visit(type, arg);
        }
    }
}