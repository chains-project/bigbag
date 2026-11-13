package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;

import java.util.List;

/**
 * Generic Spoon transformation for migrating classes that moved between packages.
 * This transformation handles import statements for classes that moved between packages.
 * 
 * Configuration: Modify OLD_PACKAGE and NEW_PACKAGE constants for your specific migration.
 */
public class Main {
    // CONFIGURATION: Update these constants for your specific migration
    // Example: When a class moves from one package to another
    // OLD_PACKAGE: The fully-qualified old package and class name
    // NEW_PACKAGE: The fully-qualified new package and class name
    private static final String OLD_PACKAGE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_PACKAGE = "eu.europa.esig.dss.enumerations.CertificationPermission";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src /path/to/transformed/src");
            System.err.println("\nConfiguration:");
            System.err.println("  Old package: " + OLD_PACKAGE);
            System.err.println("  New package: " + NEW_PACKAGE);
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying generic package migration transformation...");
        System.out.println("Migrating: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        int changes = 0;
        
        // Get all compilation units
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class) {
            @Override
            public boolean matches(CtCompilationUnit element) {
                return true;
            }
        });
        
        for (CtCompilationUnit cu : compilationUnits) {
            // Process imports
            List<CtImport> imports = cu.getImports();
            for (CtImport imp : imports) {
                String importString = imp.toString();
                // Check if import contains the old package
                if (importString.contains(OLD_PACKAGE)) {
                    // Create new import
                    CtTypeReference newTypeRef = launcher.getFactory().createReference(NEW_PACKAGE);
                    CtImport newImport = launcher.getFactory().createImport(newTypeRef);
                    imp.replace(newImport);
                    changes++;
                    System.out.println("Updated import: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
                }
            }
        }
        
        // Process type references in the code
        List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference element) {
                return element.getQualifiedName() != null && 
                       element.getQualifiedName().equals(OLD_PACKAGE);
            }
        });
        
        for (CtTypeReference typeRef : typeRefs) {
            // Replace type reference
            CtTypeReference newTypeRef = launcher.getFactory().createReference(NEW_PACKAGE);
            typeRef.replace(newTypeRef);
            changes++;
            System.out.println("Updated type reference: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
        }
        
        // Generate transformed code
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("Total changes: " + changes);
        System.out.println("Transformed files saved to: " + outputDir);
    }
}