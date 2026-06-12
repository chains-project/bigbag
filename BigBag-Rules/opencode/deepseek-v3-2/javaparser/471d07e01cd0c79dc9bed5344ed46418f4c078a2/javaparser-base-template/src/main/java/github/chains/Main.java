package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: java Main <source-directory> [old-package] [new-package]");
            System.err.println("Default: transforms com.google.api.services.cloudresourcemanager.model");
            System.err.println("         to com.google.api.services.cloudresourcemanager.v3.model");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackage = args.length > 1 ? args[1] : "com.google.api.services.cloudresourcemanager.model";
        String newPackage = args.length > 2 ? args[2] : "com.google.api.services.cloudresourcemanager.v3.model";
        
        System.out.println("Transforming files in: " + sourceDir);
        System.out.println("Transforming package: " + oldPackage + " -> " + newPackage);
        
        try {
            transformSourceFiles(sourceDir, oldPackage, newPackage);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformSourceFiles(String sourceDir, String oldPackage, String newPackage) throws Exception {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedFiles = 0;
        int transformedImports = 0;
        int transformedReferences = 0;
        
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            
            String content = Files.readString(javaFile);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
            
            boolean fileModified = false;
            
            // Transform import declarations
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                
                // Check if this is a matching import
                if (importName.startsWith(oldPackage + ".")) {
                    // Transform to new package
                    String newImportName = importName.replace(oldPackage + ".", newPackage + ".");
                    
                    importDecl.setName(newImportName);
                    fileModified = true;
                    transformedImports++;
                    System.out.println("  Transformed import: " + importName + " -> " + newImportName);
                }
            }
            
            // Transform fully-qualified class names in the code
            String transformedCode = transformFullyQualifiedNames(cu.toString(), oldPackage, newPackage);
            if (!transformedCode.equals(content)) {
                // Count transformations in the code
                int oldCount = countOccurrences(content, oldPackage + ".");
                int newCount = countOccurrences(transformedCode, newPackage + ".");
                transformedReferences += (oldCount - newCount);
                
                // Write transformed file
                try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                    writer.write(transformedCode);
                }
                fileModified = true;
            }
            
            if (fileModified) {
                transformedFiles++;
            }
        }
        
        System.out.println("\nTransformation summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + transformedFiles);
        System.out.println("  Import statements transformed: " + transformedImports);
        System.out.println("  Fully-qualified references transformed: " + transformedReferences);
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static String transformFullyQualifiedNames(String code, String oldPackage, String newPackage) {
        // Escape dots for regex
        String escapedOldPackage = oldPackage.replace(".", "\\.");
        // Replace fully-qualified class names from old to new package
        return code.replaceAll(
            escapedOldPackage + "\\.([A-Za-z0-9_]+)",
            newPackage + ".$1");
    }
    
    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}