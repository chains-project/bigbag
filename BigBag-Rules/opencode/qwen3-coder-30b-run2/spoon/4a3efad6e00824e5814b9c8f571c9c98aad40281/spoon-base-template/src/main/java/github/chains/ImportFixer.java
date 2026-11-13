package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;

public class ImportFixer {
    
    public static void main(String[] args) {
        // Create a launcher with the project source
        Launcher launcher = new Launcher();
        
        // Set the source directory
        launcher.addInputResource("/workspace/open-pdf-sign/src/main/java");
        launcher.addInputResource("/workspace/open-pdf-sign/src/test/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and fix imports that reference eu.europa.esig.dss.pades.CertificationPermission
        Factory factory = launcher.getFactory();
        boolean changed = false;
        
        for (CtImport ctImport : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() != null) {
                CtTypeReference<?> typeRef = ctImport.getReference();
                if ("eu.europa.esig.dss.pades.CertificationPermission".equals(typeRef.getQualifiedName())) {
                    // Change the import to the new location
                    CtTypeReference<?> newTypeRef = factory.createTypeReference("eu.europa.esig.dss.enumerations.CertificationPermission");
                    // Replace the reference
                    ctImport.setReference(newTypeRef);
                    System.out.println("Fixed import: " + typeRef.getQualifiedName() + " -> " + newTypeRef.getQualifiedName());
                    changed = true;
                }
            }
        }
        
        if (changed) {
            // Export the modified code
            launcher.setSourceOutputDirectory("/tmp/modified-source");
            launcher.export();
            System.out.println("Import fixing completed successfully.");
        } else {
            System.out.println("No imports needed to be fixed.");
        }
    }
}