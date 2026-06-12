package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    // Map of old package prefixes to new package prefixes for Mockito 4.x migration
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Mockito 4.x: classes moved from org.mockito.runners to org.mockito.junit
        PACKAGE_MAPPINGS.put("org.mockito.runners.", "org.mockito.junit.");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This transformation fixes Mockito 4.x breaking changes:");
            System.err.println("  - Updates imports from org.mockito.runners.* to org.mockito.junit.*");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Applying Mockito 4.x package migration fixes...");
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            JavaParser parser = new JavaParser();
            
            for (Path javaFile : javaFiles) {
                try (FileInputStream fis = new FileInputStream(javaFile.toFile())) {
                    CompilationUnit cu = parser.parse(fis).getResult().orElse(null);
                    
                    if (cu != null) {
                        boolean modified = false;
                        NodeList<ImportDeclaration> imports = cu.getImports();
                        List<ImportDeclaration> importsToRemove = new ArrayList<>();
                        List<ImportDeclaration> importsToAdd = new ArrayList<>();
                        
                        for (ImportDeclaration importDecl : imports) {
                            String importName = importDecl.getNameAsString();
                            
                            for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                                String oldPrefix = mapping.getKey();
                                String newPrefix = mapping.getValue();
                                
                                if (importName.startsWith(oldPrefix)) {
                                    System.out.println("Found old import in " + javaFile + ": " + importName);
                                    
                                    String className = importName.substring(oldPrefix.length());
                                    String newImportName = newPrefix + className;
                                    
                                    importsToRemove.add(importDecl);
                                    
                                    ImportDeclaration newImport = new ImportDeclaration(
                                        new Name(newImportName),
                                        importDecl.isStatic(),
                                        importDecl.isAsterisk()
                                    );
                                    importsToAdd.add(newImport);
                                    modified = true;
                                    System.out.println("  -> will replace with: " + newImportName);
                                    break; // Only apply one mapping per import
                                }
                            }
                        }
                        
                        if (modified) {
                            imports.removeAll(importsToRemove);
                            imports.addAll(importsToAdd);
                            
                            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                                writer.write(cu.toString());
                            }
                            
                            modifiedFiles++;
                            System.out.println("Updated imports in: " + javaFile);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("\nSuccessfully updated " + modifiedFiles + " files");
            if (modifiedFiles == 0) {
                System.out.println("No files needed updating - project already uses Mockito 4.x compatible imports");
            }
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path start) throws IOException {
        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
}