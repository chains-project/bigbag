package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation for MySQL connector package migration.
 * Handles the breaking change from MySQL connector where some classes
 * moved from com.mysql.jdbc.* to com.mysql.cj.jdbc.* packages.
 * 
 * This transformation is generic and can be applied to any project
 * affected by this breaking change.
 */
public class Main {
    
    // Define package migration patterns
    // Key: old package pattern, Value: new package replacement
    private static final Map<String, String> PACKAGE_MIGRATIONS = new HashMap<>();
    
    static {
        // MySQL connector 8.0.28 breaking changes
        // com.mysql.jdbc.exceptions.* -> com.mysql.cj.jdbc.exceptions.*
        PACKAGE_MIGRATIONS.put("com.mysql.jdbc.exceptions", "com.mysql.cj.jdbc.exceptions");
        
        // Add more patterns here as needed for other breaking changes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying MySQL connector package migration to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        int changes = applyPackageMigrations(model);
        
        System.out.println("Applied " + changes + " package migration(s)");
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        
        // Process each compilation unit
        for (CtCompilationUnit cu : model.getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            launcher.prettyprint(cu);
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static int applyPackageMigrations(CtModel model) {
        int changes = 0;
        
        // Process imports by working with compilation units directly
        for (CtCompilationUnit cu : model.getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            for (CtImport ctImport : cu.getImports()) {
                String importStr = ctImport.toString();
                for (Map.Entry<String, String> migration : PACKAGE_MIGRATIONS.entrySet()) {
                    String oldPackage = migration.getKey();
                    String newPackage = migration.getValue();
                    
                    if (importStr.contains(oldPackage)) {
                        try {
                            // Get the imported type reference
                            if (ctImport.getReference() instanceof CtTypeReference) {
                                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                                String oldQualifiedName = typeRef.getQualifiedName();
                                String newQualifiedName = oldQualifiedName.replace(oldPackage, newPackage);
                                
                                // Create new type reference with migrated package
                                CtTypeReference<?> newTypeRef = typeRef.getFactory().Type().createReference(newQualifiedName);
                                
                                // Remove old import
                                cu.removeImport(ctImport);
                                // Add new import
                                cu.addImport(newTypeRef);
                                
                                changes++;
                                System.out.println("Migrated import: " + importStr + " -> " + newQualifiedName);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to migrate import: " + importStr + ", error: " + e.getMessage());
                        }
                        break;
                    }
                }
            }
        }
        
        // Update type references in code (variable declarations, method parameters, etc.)
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qualifiedName = typeRef.getQualifiedName();
            for (Map.Entry<String, String> migration : PACKAGE_MIGRATIONS.entrySet()) {
                String oldPackage = migration.getKey();
                String newPackage = migration.getValue();
                
                if (qualifiedName.contains(oldPackage)) {
                    try {
                        String newQualifiedName = qualifiedName.replace(oldPackage, newPackage);
                        CtTypeReference<?> newTypeRef = typeRef.getFactory().Type().createReference(newQualifiedName);
                        typeRef.replace(newTypeRef);
                        changes++;
                        System.out.println("Migrated type reference: " + qualifiedName + " -> " + newQualifiedName);
                    } catch (Exception e) {
                        System.err.println("Failed to migrate type reference: " + qualifiedName + ", error: " + e.getMessage());
                    }
                    break;
                }
            }
        }
        
        return changes;
    }
}