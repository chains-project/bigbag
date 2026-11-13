package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing breaking API changes where classes
 * have moved from one package to another.
 * 
 * This transformation handles:
 * 1. Specific class imports (e.g., org.old.package.ClassName -> org.new.package.ClassName)
 * 2. Wildcard package imports (adds new package imports alongside old ones)
 */
public class Main {
    
    // Configuration: Map of old fully-qualified class names to new ones
    private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
    // Configuration: Map of old package names to new ones (for wildcard imports)
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Specific breaking change: Xpp3Dom moved from plexus-utils to maven-shared-utils
        CLASS_MAPPINGS.put(
            "org.codehaus.plexus.util.xml.Xpp3Dom",
            "org.apache.maven.shared.utils.xml.Xpp3Dom"
        );
        
        // Package mapping for wildcard imports
        PACKAGE_MAPPINGS.put(
            "org.codehaus.plexus.util.xml",
            "org.apache.maven.shared.utils.xml"
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms imports for breaking API changes where classes have moved.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming imports in: " + sourceDir);
        System.out.println("Class mappings: " + CLASS_MAPPINGS);
        System.out.println("Package mappings: " + PACKAGE_MAPPINGS);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws Exception {
        Path start = Paths.get(sourceDir);
        
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            FileInputStream in = new FileInputStream(filePath.toFile());
            
            CompilationUnit cu = parser.parse(in).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            NodeList<ImportDeclaration> imports = cu.getImports();
            
            // Process imports in reverse to safely modify the list
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration importDecl = imports.get(i);
                String importName = importDecl.getNameAsString();
                
                // Check for specific class imports that need transformation
                if (CLASS_MAPPINGS.containsKey(importName) && !importDecl.isAsterisk()) {
                    String newClassName = CLASS_MAPPINGS.get(importName);
                    ImportDeclaration newImport = new ImportDeclaration(
                        new Name(newClassName),
                        importDecl.isStatic(),
                        false
                    );
                    
                    imports.set(i, newImport);
                    modified = true;
                    System.out.println("Transformed class import " + importName + 
                                     " -> " + newClassName + " in: " + filePath);
                }
                
                // Check for wildcard package imports that need additional imports
                if (importDecl.isAsterisk() && PACKAGE_MAPPINGS.containsKey(importName)) {
                    String newPackageName = PACKAGE_MAPPINGS.get(importName);
                    
                    // Check if the new package import already exists
                    boolean alreadyExists = imports.stream()
                        .anyMatch(imp -> imp.getNameAsString().equals(newPackageName) && imp.isAsterisk());
                    
                    if (!alreadyExists) {
                        ImportDeclaration newImport = new ImportDeclaration(
                            new Name(newPackageName),
                            false,
                            true
                        );
                        
                        // Add the new import after the current one
                        imports.add(i + 1, newImport);
                        modified = true;
                        System.out.println("Added wildcard import for package " + 
                                         newPackageName + " in: " + filePath);
                    }
                }
            }
            
            if (modified) {
                // Write back the transformed file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error transforming file " + filePath + ": " + e.getMessage());
        }
    }
}