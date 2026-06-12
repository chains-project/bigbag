package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {
    
    // Map of old fully-qualified class names to new fully-qualified class names
    private static final Map<String, String> RELOCATED_CLASSES = new HashMap<>();
    private static final Map<String, String> REMOVED_CLASSES = new HashMap<>();
    
    static {
        // Package relocations: old FQCN -> new FQCN
        RELOCATED_CLASSES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        RELOCATED_CLASSES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        RELOCATED_CLASSES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        RELOCATED_CLASSES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        RELOCATED_CLASSES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        RELOCATED_CLASSES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        RELOCATED_CLASSES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        
        // Classes that were removed (with suggested replacements or empty string if no direct replacement)
        // These will need manual fixing
        REMOVED_CLASSES.put("com.hazelcast.core.MemberAttributeEvent", "");
        REMOVED_CLASSES.put("com.hazelcast.config.MaxSizeConfig", "");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
             .filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalChanges = 0;
        int removedClassesFound = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                
                if (cu != null) {
                    boolean fileChanged = false;
                    List<String> removedInThisFile = new ArrayList<>();
                    
                    // Visit all imports
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        String importName = importDecl.getNameAsString();
                        
                        // Check if this import needs to be relocated
                        if (RELOCATED_CLASSES.containsKey(importName)) {
                            String newImportName = RELOCATED_CLASSES.get(importName);
                            System.out.println(javaFile + ": Relocating import " + importName + " -> " + newImportName);
                            
                            // Create new import with updated name
                            Name newName = new Name(newImportName);
                            importDecl.setName(newName);
                            fileChanged = true;
                            totalChanges++;
                        }
                        // Check if this import is for a removed class
                        else if (REMOVED_CLASSES.containsKey(importName)) {
                            removedClassesFound++;
                            removedInThisFile.add(importName);
                            System.out.println(javaFile + ": WARNING - Import " + importName + " refers to a class that was removed in Hazelcast 5.x");
                            // Remove the import since the class no longer exists
                            cu.remove(importDecl);
                            fileChanged = true;
                        }
                    }
                    
                    // Save the file if changes were made
                    if (fileChanged) {
                        String newContent = cu.toString();
                        Files.write(javaFile, newContent.getBytes());
                        System.out.println(javaFile + ": Updated");
                    }
                    
                    // Print summary of removed classes for this file
                    if (!removedInThisFile.isEmpty()) {
                        System.out.println(javaFile + ": The following imports refer to removed classes and need manual fixing:");
                        for (String removedClass : removedInThisFile) {
                            System.out.println("  - " + removedClass);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Total relocated imports: " + totalChanges);
        System.out.println("Total references to removed classes: " + removedClassesFound);
        System.out.println("\nNote: Classes that were removed (MemberAttributeEvent, MaxSizeConfig) need manual code fixes.");
        System.out.println("The transformation only handles package relocations automatically.");
    }
}