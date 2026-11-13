package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * A generic Spoon transformation that migrates javax.validation imports to jakarta.validation
 * for projects upgrading to Hibernate Validator 8.0+.
 * 
 * This transformation handles:
 * 1. Import statements (e.g., import javax.validation.Valid; -> import jakarta.validation.Valid;)
 */
public class Main {
    
    // Configuration parameters - can be changed for different migration scenarios
    private static final String OLD_PACKAGE_PREFIX = "javax.validation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.validation";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Migrating " + OLD_PACKAGE_PREFIX + " -> " + NEW_PACKAGE_PREFIX);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        System.out.println("Building Spoon model...");
        launcher.buildModel();
        CtModel model = launcher.getModel();
        
        // Track changes for reporting
        Set<String> changedFiles = new HashSet<>();
        int importChanges = 0;
        
        // Process imports
        System.out.println("Processing imports...");
        for (CtImport ctImport : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            String importStr = ctImport.getReference().toString();
            System.out.println("  Checking import: " + importStr);
            if (importStr.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImport = importStr.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                System.out.println("    Found import to migrate: " + importStr);
                
                // Create new import reference
                if (ctImport.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                    // Create new type reference with the updated package
                    CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newImport);
                    
                    // Update the import
                    ctImport.setReference(newTypeRef);
                    importChanges++;
                    
                    // Track changed file
                    CtType<?> parentType = ctImport.getParent(CtType.class);
                    if (parentType != null && parentType.getPosition() != null && parentType.getPosition().getFile() != null) {
                        changedFiles.add(parentType.getPosition().getFile().getAbsolutePath());
                    }
                    
                    System.out.println("    Updated import: " + importStr + " -> " + newImport);
                }
            }
        }
        
        // Process type references in code (fully qualified names)
        System.out.println("Processing fully qualified type references in code...");
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newQualifiedName = qualifiedName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                
                // For fully qualified names in code, we need to update them
                // This handles cases like "javax.validation.Valid annotation = ..."
                typeRef.setSimpleName(getSimpleName(newQualifiedName));
                
                // Track changed file
                CtType<?> parentType = typeRef.getParent(CtType.class);
                if (parentType != null && parentType.getPosition() != null && parentType.getPosition().getFile() != null) {
                    changedFiles.add(parentType.getPosition().getFile().getAbsolutePath());
                }
                
                System.out.println("  Updated type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        }
        
        // Print transformation summary
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Package migration: " + OLD_PACKAGE_PREFIX + " -> " + NEW_PACKAGE_PREFIX);
        System.out.println("Import statements updated: " + importChanges);
        System.out.println("Files modified: " + changedFiles.size());
        
        if (!changedFiles.isEmpty()) {
            System.out.println("\nModified files:");
            for (String file : changedFiles) {
                System.out.println("  " + file);
            }
        }
        
        // Apply changes to source files
        System.out.println("\nWriting changes to source files...");
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
    }
    
    private static String getSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }
}