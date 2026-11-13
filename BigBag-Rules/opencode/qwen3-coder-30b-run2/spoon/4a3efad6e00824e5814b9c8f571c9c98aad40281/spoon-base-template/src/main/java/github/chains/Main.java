package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <sourceDir> <targetDir>");
            System.exit(1);
        }

        String sourceDir = args[0];
        String targetDir = args[1];

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(targetDir);
        
        // Set the Java version compatibility
        launcher.getEnvironment().setComplianceLevel(8);

        // Build the model
        CtModel model = launcher.buildModel();

        // Find all imports from the problematic package and fix them
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        int importsChanged = 0;
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                CtTypeReference<?> typeRef = imp.getReference();
                // Check if this import refers to the moved class
                if (typeRef.getQualifiedName().equals("eu.europa.esig.dss.pades.CertificationPermission")) {
                    // Replace the import with the new qualified name
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createTypeReference("eu.europa.esig.dss.enumerations.CertificationPermission");
                    imp.setReference(newTypeRef);
                    importsChanged++;
                }
            }
        }

        // Write the transformed code
        launcher.setSourceOutputDirectory(targetDir);
        launcher.process();
        launcher.prettyprint();

        System.out.println("Transformation completed. Updated " + importsChanged + " import statements.");
    }
}

        String sourceDir = args[0];
        String targetDir = args[1];

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(targetDir);
        
        // Set the Java version compatibility
        launcher.getEnvironment().setComplianceLevel(8);

        // Process the code
        CtModel model = launcher.buildModel();

        // Find all imports that need to be updated
        List<CtImport> importsToRemove = new ArrayList<>();
        List<CtImport> importsToAdd = new ArrayList<>();

        // Pattern for the old import that needs to be replaced
        String oldImportPackage = "eu.europa.esig.dss.pades";
        String oldImportType = "CertificationPermission";
        String newImportPackage = "eu.europa.esig.dss.enumerations";
        String newImportType = "CertificationPermission";

        // Search for imports to fix
        for (CtImport imp : model.getAllTypes()) {
            if (imp.getReference() != null) {
                CtTypeReference<?> typeRef = imp.getReference();
                if (typeRef.getQualifiedName().equals(oldImportPackage + "." + oldImportType)) {
                    // Remove the old import
                    importsToRemove.add(imp);
                    
                    // Create new import
                    CtImport newImport = launcher.getFactory().createImport(
                        launcher.getFactory().createTypeReference(
                            launcher.getFactory().createQualifiedTypeReference(
                                newImportPackage,
                                newImportType
                            )
                        )
                    );
                    importsToAdd.add(newImport);
                }
            }
        }

        // Apply transformations
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }

        for (CtImport imp : importsToAdd) {
            model.getUnnamedModule().addImport(imp);
        }

        // Write the transformed code
        launcher.setSourceOutputDirectory(targetDir);
        launcher.process();
        launcher.prettyprint();

        System.out.println("Transformation completed. Check " + targetDir + " for the updated source.");
    }
}