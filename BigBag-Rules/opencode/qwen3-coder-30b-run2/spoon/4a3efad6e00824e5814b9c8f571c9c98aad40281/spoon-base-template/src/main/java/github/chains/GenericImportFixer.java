package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import java.util.List;
import java.util.ArrayList;

public class GenericImportFixer {
    
    // Configuration for the transformation
    private static final String OLD_PACKAGE = "eu.europa.esig.dss.pades";
    private static final String NEW_PACKAGE = "eu.europa.esig.dss.enumerations";
    private static final String TARGET_CLASS = "CertificationPermission";
    
    public static void main(String[] args) {
        // Create a launcher with the project source
        Launcher launcher = new Launcher();
        
        // Set the source directory
        launcher.addInputResource("/workspace/open-pdf-sign/src/main/java");
        launcher.addInputResource("/workspace/open-pdf-sign/src/test/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and fix imports that reference the target class in the old package
        Factory factory = launcher.getFactory();
        boolean changed = false;
        
        List<CtImport> importsToFix = new ArrayList<>();
        for (CtImport ctImport : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() != null) {
                CtTypeReference<?> typeRef = ctImport.getReference();
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName.equals(OLD_PACKAGE + "." + TARGET_CLASS)) {
                    importsToFix.add(ctImport);
                }
            }
        }
        
        // Apply fixes
        for (CtImport ctImport : importsToFix) {
            if (ctImport.getReference() != null) {
                CtTypeReference<?> typeRef = ctImport.getReference();
                // Create new type reference with updated package
                CtTypeReference<?> newTypeRef = factory.createTypeReference(NEW_PACKAGE + "." + TARGET_CLASS);
                // Replace the reference
                ctImport.setReference(newTypeRef);
                System.out.println("Fixed import: " + typeRef.getQualifiedName() + " -> " + newTypeRef.getQualifiedName());
                changed = true;
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