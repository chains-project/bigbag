package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generic transformation for migrating Java EE (javax.*) APIs to Jakarta EE (jakarta.*) APIs.
 * This is a SIMPLIFIED transformation that focuses on the most common cases:
 * 1. Import statements
 * 2. Simple text replacement for fully qualified names in code
 * 
 * The transformation is parameterized and can be extended for other package migrations.
 */
public class Main {
    
    // Configuration: map of old package prefixes to new package prefixes
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Java EE to Jakarta EE migrations
        PACKAGE_MAPPINGS.put("javax.servlet", "jakarta.servlet");
        PACKAGE_MAPPINGS.put("javax.ws.rs", "jakarta.ws.rs");
        PACKAGE_MAPPINGS.put("javax.validation", "jakarta.validation");
        PACKAGE_MAPPINGS.put("javax.annotation", "jakarta.annotation");
        PACKAGE_MAPPINGS.put("javax.ejb", "jakarta.ejb");
        PACKAGE_MAPPINGS.put("javax.persistence", "jakarta.persistence");
        PACKAGE_MAPPINGS.put("javax.transaction", "jakarta.transaction");
        PACKAGE_MAPPINGS.put("javax.security", "jakarta.security");
        PACKAGE_MAPPINGS.put("javax.enterprise", "jakarta.enterprise");
        PACKAGE_MAPPINGS.put("javax.inject", "jakarta.inject");
        PACKAGE_MAPPINGS.put("javax.json", "jakarta.json");
        PACKAGE_MAPPINGS.put("javax.jms", "jakarta.jms");
        PACKAGE_MAPPINGS.put("javax.mail", "jakarta.mail");
        PACKAGE_MAPPINGS.put("javax.xml.bind", "jakarta.xml.bind");
        PACKAGE_MAPPINGS.put("javax.xml.ws", "jakarta.xml.ws");
        PACKAGE_MAPPINGS.put("javax.jws", "jakarta.jws");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Java EE (javax.*) imports and type references to Jakarta EE (jakarta.*)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            int transformedFiles = transformJavaFiles(sourcePath);
            System.out.println("Transformation completed successfully. Transformed " + transformedFiles + " files.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformJavaFiles(Path sourceDir) throws IOException {
        JavaParser javaParser = new JavaParser();
        final int[] fileCount = {0};
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     if (processJavaFile(javaFile, javaParser)) {
                         fileCount[0]++;
                     }
                 });
        }
        
        return fileCount[0];
    }
    
    private static boolean processJavaFile(Path javaFile, JavaParser javaParser) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(javaFile));
            String originalContent = content;
            
            // Transform imports using JavaParser
            CompilationUnit cu = javaParser.parse(content).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile)
            );
            
            boolean modified = false;
            
            // 1. Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                String transformed = transformPackage(importName);
                if (!importName.equals(transformed)) {
                    importDecl.setName(transformed);
                    modified = true;
                    System.out.println("  Transformed import: " + importName + " -> " + transformed);
                }
            }
            
            if (modified) {
                // Write the modified file back
                String transformedCode = cu.toString();
                Files.write(javaFile, transformedCode.getBytes());
                System.out.println("Updated file (imports only): " + javaFile);
                
                // Now also do a simple text replacement for fully qualified names in the code
                // This handles cases like:
                // - javax.servlet.Filter in method parameters
                // - javax.servlet.DispatcherType in code
                // - etc.
                content = transformedCode;
                String textTransformed = transformTextContent(content);
                if (!content.equals(textTransformed)) {
                    Files.write(javaFile, textTransformed.getBytes());
                    System.out.println("Updated file (text replacements): " + javaFile);
                }
                
                return true;
            } else {
                // Even if imports weren't modified, check for fully qualified names in the code
                String textTransformed = transformTextContent(content);
                if (!content.equals(textTransformed)) {
                    Files.write(javaFile, textTransformed.getBytes());
                    System.out.println("Updated file (text replacements only): " + javaFile);
                    return true;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Transform text content by replacing fully qualified javax.* names with jakarta.*
     * This is a simple text replacement that handles:
     * - Fully qualified type names in code
     * - Fully qualified names in annotations
     * - etc.
     */
    private static String transformTextContent(String content) {
        String transformed = content;
        
        // For each package mapping, replace occurrences in the text
        // We use word boundaries to avoid replacing partial matches
        for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
            String oldPrefix = mapping.getKey();
            String newPrefix = mapping.getValue();
            
            // Pattern: word boundary followed by the old prefix followed by a dot or end of word
            // This avoids replacing things like "my.javax.custom.Class"
            String regex = "\\b" + Pattern.quote(oldPrefix) + "\\.";
            transformed = transformed.replaceAll(regex, newPrefix + ".");
        }
        
        return transformed;
    }
    
    /**
     * Transforms a package name or fully qualified type name from javax.* to jakarta.*
     * if it matches any of the configured package mappings.
     */
    private static String transformPackage(String name) {
        for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
            String oldPrefix = mapping.getKey();
            String newPrefix = mapping.getValue();
            
            // Exact match (e.g., "javax.servlet" -> "jakarta.servlet")
            if (name.equals(oldPrefix)) {
                return newPrefix;
            }
            
            // Prefix match (e.g., "javax.servlet.Filter" -> "jakarta.servlet.Filter")
            if (name.startsWith(oldPrefix + ".")) {
                return newPrefix + name.substring(oldPrefix.length());
            }
        }
        
        return name;
    }
}