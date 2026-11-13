package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.StandardEnvironment;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Dropwizard 2.x to 4.x migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourceDir);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            int changes = 0;
            
            // Define migration patterns: old package -> new package
            // For Dropwizard 2.x to 4.x migration
            // setup.* classes moved to core.setup.*
            String[][] migrationPatterns = {
                {"io.dropwizard.setup.", "io.dropwizard.core.setup."}
            };
            
            // Find all type references to io.dropwizard.setup.* classes
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    String qualifiedName = typeRef.getQualifiedName();
                    return qualifiedName != null && qualifiedName.startsWith("io.dropwizard.setup.");
                }
            });
            
            // Update type references
            for (CtTypeReference<?> typeRef : typeRefs) {
                String oldName = typeRef.getQualifiedName();
                String newName = oldName.replace("io.dropwizard.setup.", "io.dropwizard.core.setup.");
                
                // Create new type reference
                CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newName);
                typeRef.replace(newTypeRef);
                
                System.out.println("Updated type reference: " + oldName + " -> " + newName);
                changes++;
            }
            
            // Find and update imports
            List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport ctImport) {
                    // Check if it's a type import
                    Object importRef = ctImport.getReference();
                    if (importRef instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) importRef;
                        String qualifiedName = typeRef.getQualifiedName();
                        return qualifiedName != null && qualifiedName.startsWith("io.dropwizard.setup.");
                    }
                    return false;
                }
            });
            
            for (CtImport ctImport : imports) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String oldImport = typeRef.getQualifiedName();
                String newImport = oldImport.replace("io.dropwizard.setup.", "io.dropwizard.core.setup.");
                
                // Create new import
                CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newImport);
                CtImport newImportDecl = ctImport.getFactory().createImport(newTypeRef);
                ctImport.replace(newImportDecl);
                
                System.out.println("Updated import: " + oldImport + " -> " + newImport);
                changes++;
            }
            
            System.out.println("Total changes applied: " + changes);
            
            if (changes > 0) {
                // Create output directory
                File outputDir = new File(sourceDir + "-transformed");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                // Apply transformation and write output
                launcher.setSourceOutputDirectory(outputDir.getAbsolutePath());
                launcher.prettyprint();
                
                System.out.println("Transformation complete. Output written to: " + outputDir.getAbsolutePath());
            } else {
                System.out.println("No changes needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}