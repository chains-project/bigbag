package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic Spoon transformation for fixing breaking dependency changes
 * caused by package restructuring in APIs.
 * 
 * This transformation handles package migration patterns such as:
 * - org.apache.struts2.dispatcher.ng.filter -> org.apache.struts2.dispatcher.filter
 * - Any similar package structure changes
 * 
 * The transformation is parameterized by package mappings and can be
 * reused for any similar breaking changes in dependencies.
 */
public class Main {
    
    // Configuration for the transformation - could be loaded from config file
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Example: Struts2 migration
        PACKAGE_MAPPINGS.put("org.apache.struts2.dispatcher.ng.filter", 
                            "org.apache.struts2.dispatcher.filter");
        // Add more mappings as needed for other breaking changes
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("       java -jar spoon-transformation.jar <source-directory> <old-package> <new-package>");
            System.err.println("Example 1 (pre-configured): java -jar spoon-transformation.jar /path/to/project/src");
            System.err.println("Example 2 (custom): java -jar spoon-transformation.jar /path/to/project/src com.old.package com.new.package");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Allow custom package mappings via command line
        Map<String, String> mappings = new HashMap<>(PACKAGE_MAPPINGS);
        if (args.length >= 3) {
            mappings.clear();
            mappings.put(args[1], args[2]);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Package mappings to apply:");
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            System.out.println("  " + mapping.getKey() + " -> " + mapping.getValue());
        }
        
        if (!Files.exists(Paths.get(sourceDir))) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            int totalChanges = 0;
            
            // Get all type references in the code
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                
                if (qualifiedName == null) {
                    continue;
                }
                
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    String oldPackage = mapping.getKey();
                    String newPackage = mapping.getValue();
                    
                    if (qualifiedName.startsWith(oldPackage)) {
                        String newQualifiedName = qualifiedName.replace(oldPackage, newPackage);
                        
                        try {
                            // Create new type reference
                            CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                            
                            // Copy type arguments if present
                            if (!typeRef.getActualTypeArguments().isEmpty()) {
                                newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                            }
                            
                            // Replace the old reference with the new one
                            typeRef.replace(newTypeRef);
                            
                            System.out.println("Updated type reference: " + qualifiedName + " -> " + newQualifiedName);
                            totalChanges++;
                        } catch (Exception e) {
                            System.err.println("Failed to update type reference: " + qualifiedName + " - " + e.getMessage());
                        }
                    }
                }
            }
            
            // Apply the transformations
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("\nTransformation completed:");
            System.out.println("  Total type references updated: " + totalChanges);
            
            // Provide instructions for import statement updates
            // (Spoon's import handling can be complex, so we provide a fallback)
            if (totalChanges > 0) {
                System.out.println("\nNote: Import statements may also need updating.");
                System.out.println("      To update imports, run:");
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    String oldPackagePattern = mapping.getKey().replace(".", "\\.");
                    String newPackage = mapping.getValue();
                    System.out.println("      find " + sourceDir + " -name \"*.java\" -type f -exec sed -i 's/" + oldPackagePattern + "/" + newPackage + "/g' {} \\;");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Helper method to validate that a transformation will work correctly
     */
    private static void validateTransformation(String sourceDir, Map<String, String> mappings) {
        System.out.println("Validating transformation parameters...");
        
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            String oldPackage = mapping.getKey();
            String newPackage = mapping.getValue();
            
            if (oldPackage.equals(newPackage)) {
                System.err.println("Error: Old and new packages are identical: " + oldPackage);
                System.exit(1);
            }
            
            if (!oldPackage.contains(".") || !newPackage.contains(".")) {
                System.err.println("Warning: Package '" + oldPackage + "' -> '" + newPackage + "' may not be valid package names");
            }
        }
        
        System.out.println("Validation passed.");
    }
}