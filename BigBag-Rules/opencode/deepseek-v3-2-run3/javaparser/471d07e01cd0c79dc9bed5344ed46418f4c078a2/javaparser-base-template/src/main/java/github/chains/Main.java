package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic JavaParser transformation for handling API version upgrade breaking changes.
 * 
 * This transformation handles the common pattern where APIs move from v1/v2 to v3,
 * requiring package updates by inserting ".v3" before certain package components.
 * 
 * The transformation supports multiple patterns to handle different package structures:
 * 1. model.* -> v3.model.*
 * 2. CloudResourceManager -> v3.CloudResourceManager  
 * 
 * Usage: java Main <source-directory> <base-package> <version-to-insert>
 * Example: java Main /path/to/src "com.google.api.services.cloudresourcemanager" "v3"
 * 
 * This will transform:
 * - com.google.api.services.cloudresourcemanager.model.* -> com.google.api.services.cloudresourcemanager.v3.model.*
 * - com.google.api.services.cloudresourcemanager.CloudResourceManager -> com.google.api.services.cloudresourcemanager.v3.CloudResourceManager
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <source-directory> <base-package> <version-to-insert>");
            System.err.println("Example: java Main /path/to/src \\");
            System.err.println("        \"com.google.api.services.cloudresourcemanager\" \\");
            System.err.println("        \"v3\"");
            System.err.println("");
            System.err.println("This transformation will insert the version before:");
            System.err.println("1. '.model' package component");
            System.err.println("2. Last package component (for main client classes)");
            System.err.println("3. Other common patterns");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String basePackage = args[1];
        String version = args[2];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Base package: " + basePackage);
        System.out.println("Inserting version: " + version);
        
        // Define transformation patterns
        String[] patterns = {
            basePackage + ".model",  // model classes
            basePackage + ".CloudResourceManager",  // main client class
            basePackage + ".CloudResourceManager$", // inner classes
        };
        
        String[] replacements = {
            basePackage + "." + version + ".model",
            basePackage + "." + version + ".CloudResourceManager",
            basePackage + "." + version + ".CloudResourceManager$",
        };
        
        try {
            processDirectory(Paths.get(sourceDir), patterns, replacements);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory, String[] patterns, String[] replacements) 
            throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> processJavaFile(path, patterns, replacements));
    }
    
    private static void processJavaFile(Path javaFile, String[] patterns, String[] replacements) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return;
            }
            
            boolean modified = false;
            String originalContent = cu.toString();
            String transformedContent = originalContent;
            
            // Check if file contains any of the patterns
            boolean containsPattern = false;
            for (String pattern : patterns) {
                if (originalContent.contains(pattern.replace("$", ""))) { // Remove $ for checking
                    containsPattern = true;
                    break;
                }
            }
            
            if (!containsPattern) {
                return; // Nothing to do for this file
            }
            
            // Transform imports
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                String originalImport = importName;
                
                for (int i = 0; i < patterns.length; i++) {
                    if (importName.startsWith(patterns[i])) {
                        String newImportName = importName.replace(patterns[i], replacements[i]);
                        importDecl.setName(newImportName);
                        System.out.println("Updated import: " + originalImport + " -> " + newImportName + " in " + javaFile);
                        modified = true;
                        break;
                    }
                }
            }
            
            // Transform the entire content (handles FQNs in code, comments, annotations, etc.)
            transformedContent = cu.toString();
            for (int i = 0; i < patterns.length; i++) {
                if (transformedContent.contains(patterns[i].replace("$", ""))) {
                    transformedContent = transformedContent.replace(patterns[i], replacements[i]);
                    System.out.println("Updated pattern '" + patterns[i] + "' in code for: " + javaFile);
                    modified = true;
                }
            }
            
            if (modified) {
                // Re-parse to ensure valid syntax
                cu = parser.parse(transformedContent).getResult().orElse(cu);
                
                // Write the modified file back
                try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Saved modifications to: " + javaFile);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}