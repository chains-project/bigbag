package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.err.println("Fixes breaking changes from http v1.0.x to v1.1.1:");
            System.err.println("  - Changes imports of org.cactoos.io.BytesOf to org.cactoos.bytes.BytesOf");
            System.err.println("  - Note: You may also need to add cactoos as a direct dependency");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws Exception {
        JavaParser parser = new JavaParser();
        
        // Map of old imports to new imports for breaking changes
        // This can be extended with more mappings as needed
        Map<String, String> importMappings = new HashMap<>();
        importMappings.put("org.cactoos.io.BytesOf", "org.cactoos.bytes.BytesOf");
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                        () -> new RuntimeException("Failed to parse " + javaFile)
                    );
                    
                    boolean modified = false;
                    NodeList<ImportDeclaration> imports = cu.getImports();
                    for (int i = 0; i < imports.size(); i++) {
                        ImportDeclaration imp = imports.get(i);
                        String importName = imp.getNameAsString();
                        
                        // Check if this import needs to be remapped
                        if (importMappings.containsKey(importName)) {
                            String newImport = importMappings.get(importName);
                            imports.set(i, new ImportDeclaration(
                                new Name(newImport),
                                imp.isStatic(),
                                imp.isAsterisk()
                            ));
                            modified = true;
                            System.out.println("Fixed import in " + javaFile + ": " + importName + " -> " + newImport);
                        }
                        
                        // Also warn about other imports from org.cactoos.io that might need attention
                        if (importName.startsWith("org.cactoos.io.") && !importName.endsWith(".*")) {
                            if (!importMappings.containsKey(importName)) {
                                System.out.println("Warning: Import from potentially deprecated package org.cactoos.io: " + importName + " in " + javaFile);
                                System.out.println("  This class may have moved to a different package in cactoos.");
                            }
                        }
                    }
                    
                    if (modified) {
                        Files.write(javaFile, cu.toString().getBytes());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            });
    }
}