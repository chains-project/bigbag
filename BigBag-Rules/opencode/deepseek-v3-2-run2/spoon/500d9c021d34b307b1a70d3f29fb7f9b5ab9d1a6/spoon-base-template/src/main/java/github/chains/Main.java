package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * A generic Spoon transformation that migrates javax.mvc API calls to jakarta.mvc API.
 * This handles the breaking change from Java EE 8 (javax.*) to Jakarta EE 9+ (jakarta.*).
 * 
 * The transformation is parameterized by:
 * - OLD_BASE_PACKAGE: The old package prefix to replace (e.g., "javax.mvc")
 * - NEW_BASE_PACKAGE: The new package prefix to use (e.g., "jakarta.mvc")
 * 
 * This transformation handles:
 * 1. Import statements
 * 2. Fully-qualified type references in code
 * 3. Type references in annotations, method signatures, field declarations, etc.
 */
public class Main {
    
    // Configuration parameters - can be changed for different migration scenarios
    private static final String OLD_BASE_PACKAGE = "javax.mvc";
    private static final String NEW_BASE_PACKAGE = "jakarta.mvc";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-dir> <output-dir>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Starting migration from " + OLD_BASE_PACKAGE + " to " + NEW_BASE_PACKAGE);
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add processor for type references
            launcher.addProcessor(new TypeReferenceMigrationProcessor());
            
            // Run the transformation
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor that migrates all type references from old package to new package.
     * This includes type references in imports, annotations, field declarations, etc.
     */
    private static class TypeReferenceMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Skip null references or references without qualified names
            if (typeRef == null || typeRef.getQualifiedName() == null) {
                return;
            }
            
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if this type reference starts with the old package
            if (qualifiedName.startsWith(OLD_BASE_PACKAGE)) {
                // Replace the package prefix
                String newQualifiedName = qualifiedName.replaceFirst("^" + OLD_BASE_PACKAGE, NEW_BASE_PACKAGE);
                
                // Create a new type reference with the updated package
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                
                // Copy type arguments if present
                if (!typeRef.getActualTypeArguments().isEmpty()) {
                    newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                }
                
                // Replace the old reference with the new one
                try {
                    typeRef.replace(newTypeRef);
                    System.out.println("Migrated: " + qualifiedName + " -> " + newQualifiedName);
                } catch (Exception e) {
                    System.err.println("Failed to migrate: " + qualifiedName);
                }
            }
        }
    }
}