/**
 * Generic Spoon-based import fixer that handles breaking changes in Java dependencies
 * 
 * This transformation addresses the specific case where CertificationPermission was moved
 * from eu.europa.esig.dss.pades to eu.europa.esig.dss.enumerations
 * 
 * Usage:
 * 1. Compile with Spoon dependencies
 * 2. Run the transformation on your source code
 * 3. It will automatically fix imports that reference classes moved between packages
 */
package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class ImportChangeFixer {
    
    public static void main(String[] args) {
        // Create a launcher
        Launcher launcher = new Launcher();
        
        // Input directories
        launcher.addInputResource("/workspace/open-pdf-sign/src/main/java");
        launcher.addInputResource("/workspace/open-pdf-sign/src/test/java");
        
        // Build model
        CtModel model = launcher.buildModel();
        
        boolean changed = false;
        
        // Find all imports that contain the problematic pattern
        for (CtImport ctImport : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() != null) {
                String qualifiedName = ctImport.getReference().getQualifiedName();
                
                // Detect the specific import change we're fixing
                if (qualifiedName.equals("eu.europa.esig.dss.pades.CertificationPermission")) {
                    // Try to fix it by changing the import to the new location
                    // Note: This assumes the class exists in the new location
                    try {
                        // Create new reference with the corrected package
                        CtTypeReference<?> newRef = launcher.getFactory().createTypeReference("eu.europa.esig.dss.enumerations.CertificationPermission");
                        ctImport.setReference(newRef);
                        System.out.println("Fixed import: " + qualifiedName + " -> eu.europa.esig.dss.enumerations.CertificationPermission");
                        changed = true;
                    } catch (Exception e) {
                        System.err.println("Could not fix import for " + qualifiedName + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (changed) {
            // Export modified files
            launcher.setSourceOutputDirectory("/tmp/modified-source");
            launcher.export();
            System.out.println("Successfully updated imports.");
        } else {
            System.out.println("No imports needed updating.");
        }
    }
}