package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic Spoon transformation for migrating from javax.annotation to jakarta.annotation
 * as part of the Jakarta EE migration.
 * 
 * This transformation handles:
 * 1. Import statement updates: javax.annotation.* -> jakarta.annotation.*
 * 2. Type reference updates in code annotations, field declarations, etc.
 * 
 * The transformation is parameterized to work with any project by simply
 * changing the input source directory path.
 */
public class Main {
    
    // Configuration: Map of old package prefixes to new package prefixes
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // javax.annotation -> jakarta.annotation mapping
        PACKAGE_MAPPINGS.put("javax.annotation", "jakarta.annotation");
        // Add other javax to jakarta mappings as needed
        // PACKAGE_MAPPINGS.put("javax.inject", "jakarta.inject");
        // PACKAGE_MAPPINGS.put("javax.enterprise", "jakarta.enterprise");
        // etc.
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/transformed/src");
            System.err.println("\nThis transformation migrates javax.annotation.* imports to jakarta.annotation.*");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Jakarta EE javax.annotation Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Package mappings:");
        for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println();
        
        // Create launcher for the transformation
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        System.out.println("Building Spoon model...");
        CtModel model = launcher.buildModel();
        
        // Get all types in the model
        List<CtType<?>> allTypes = model.getAllTypes().stream().toList();
        System.out.println("Found " + allTypes.size() + " types to process");
        
        int typeReferenceChanges = 0;
        
        // Process each type
        for (CtType<?> type : allTypes) {
            String typeName = type.getQualifiedName();
            
            // Get the compilation unit to access imports
            CtCompilationUnit compilationUnit = type.getPosition().getCompilationUnit();
            if (compilationUnit != null) {
                // Process imports in the compilation unit
                List<CtImport> imports = compilationUnit.getImports();
                for (CtImport ctImport : imports) {
                    String importStr = ctImport.toString();
                    for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                        if (importStr.contains(mapping.getKey() + ".")) {
                            // Get the reference from the import - need to check if it's a type reference
                            if (ctImport.getReference() instanceof CtTypeReference) {
                                CtTypeReference<?> importedType = (CtTypeReference<?>) ctImport.getReference();
                                String oldQualifiedName = importedType.getQualifiedName();
                                if (oldQualifiedName.startsWith(mapping.getKey() + ".")) {
                                    String newQualifiedName = oldQualifiedName.replace(mapping.getKey() + ".", mapping.getValue() + ".");
                                    // Create new import with the updated reference
                                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newQualifiedName);
                                    CtImport newImport = launcher.getFactory().createImport(newTypeRef);
                                    // Replace the import
                                    ctImport.replace(newImport);
                                    System.out.println("[" + typeName + "] Updated import: " + oldQualifiedName + " -> " + newQualifiedName);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            // Update type references in the code
            // Find all type references that match our package mappings
            List<CtTypeReference<?>> typeRefs = Query.getElements(type, new Filter<CtTypeReference<?>>() {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    String qualifiedName = typeRef.getQualifiedName();
                    for (String oldPrefix : PACKAGE_MAPPINGS.keySet()) {
                        if (qualifiedName.startsWith(oldPrefix + ".")) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                String oldQualifiedName = typeRef.getQualifiedName();
                String newQualifiedName = oldQualifiedName;
                
                for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                    if (oldQualifiedName.startsWith(mapping.getKey() + ".")) {
                        newQualifiedName = oldQualifiedName.replace(mapping.getKey() + ".", mapping.getValue() + ".");
                        break;
                    }
                }
                
                if (!oldQualifiedName.equals(newQualifiedName)) {
                    // Create a new type reference with the updated package
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newQualifiedName);
                    
                    // Preserve any type arguments
                    if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                        newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                    }
                    
                    // Replace the old type reference with the new one
                    typeRef.replace(newTypeRef);
                    typeReferenceChanges++;
                    System.out.println("[" + typeName + "] Updated type reference: " + oldQualifiedName + " -> " + newQualifiedName);
                }
            }
        }
        
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Type references updated: " + typeReferenceChanges);
        
        // Apply the transformation
        System.out.println("\nWriting transformed code to: " + outputDir);
        launcher.prettyprint();
        
        System.out.println("\n=== Transformation Completed Successfully ===");
        System.out.println("The code has been migrated from javax.annotation to jakarta.annotation.");
        System.out.println("Please verify the changes and run your build system to confirm compilation.");
    }
}