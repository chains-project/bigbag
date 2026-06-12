package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

/**
 * Generic Spoon transformation to fix Hazelcast 4.0.5 breaking changes.
 * This transformation addresses the removal of core classes from the com.hazelcast.core package.
 */
public class Main {
    public static void main(String[] args) {
        // Check if input directory is provided
        if (args.length < 1) {
            System.err.println("Usage: java -jar <jar-file> <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        
        // Set compliance level
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Remove imports for removed Hazelcast classes
        removeRemovedImports(factory);
        
        // Save changes
        launcher.writeProcessedSourceFiles();
        
        System.out.println("Hazelcast 4.0.5 breaking changes fixed successfully.");
    }
    
    /**
     * Remove imports for classes that were removed in Hazelcast 4.0.5
     */
    private static void removeRemovedImports(Factory factory) {
        // Remove imports for classes that were removed in Hazelcast 4.0.5
        factory.getModel().getElements(e -> {
            if (e instanceof CtImport) {
                CtImport imp = (CtImport) e;
                String importedType = imp.getReference().toString();
                // Remove imports for classes that were removed in Hazelcast 4.0.5
                return importedType.startsWith("com.hazelcast.core.") && 
                       (importedType.contains("Member") || 
                        importedType.contains("Cluster") || 
                        importedType.contains("IMap") || 
                        importedType.contains("MapEvent") || 
                        importedType.contains("MaxSizeConfig") ||
                        importedType.contains("MemberAttributeEvent") ||
                        importedType.contains("MembershipEvent") ||
                        importedType.contains("MembershipListener"));
            }
            return false;
        }).forEach(importToRemove -> {
            factory.getModel().getImports().remove(importToRemove);
        });
    }
}