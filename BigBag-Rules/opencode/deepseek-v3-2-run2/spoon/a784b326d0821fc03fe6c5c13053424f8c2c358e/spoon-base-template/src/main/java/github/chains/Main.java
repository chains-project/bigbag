package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * GENERIC TRANSFORMATION RULE: javax.validation to jakarta.validation migration
 * 
 * BREAKING CHANGE: Hibernate Validator 8.0.0.Final migrated from Java EE (javax) 
 * to Jakarta EE (jakarta) namespace.
 * 
 * OLD API PATTERN: javax.validation.* (e.g., javax.validation.Valid)
 * NEW API PATTERN: jakarta.validation.* (e.g., jakarta.validation.Valid)
 * 
 * STRUCTURAL TRANSFORMATION REQUIRED:
 * 1. Replace all occurrences of "javax.validation" with "jakarta.validation"
 * 2. Applies to: import statements, fully qualified type references, package references
 * 
 * This transformation is GENERIC and REUSABLE for ANY project affected by this
 * Hibernate Validator breaking change.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [output-directory]");
            System.err.println("  Transforms javax.validation -> jakarta.validation for Hibernate Validator 8.x");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        System.out.println("Transforming javax.validation -> jakarta.validation");
        System.out.println("Source: " + sourceDir + " -> Output: " + outputDir);
        
        try {
            transform(sourceDir, outputDir);
            System.out.println("Transformation completed!");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void transform(String sourceDir, String outputDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        
        CtModel model = launcher.buildModel();
        
        transformImports(model);
        transformTypeReferences(model);
        transformPackageReferences(model);
        
        launcher.prettyprint();
    }
    
    private static void transformImports(CtModel model) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            CtTypeReference<?> importedRef = (CtTypeReference<?>) ctImport.getReference();
            if (importedRef != null) {
                String name = importedRef.getQualifiedName();
                if (name != null && name.startsWith("javax.validation.")) {
                    String newName = name.replace("javax.validation", "jakarta.validation");
                    CtTypeReference<?> newRef = ctImport.getFactory().createReference(newName);
                    ctImport.setReference(newRef);
                    System.out.println("Transformed import: " + name + " -> " + newName);
                }
            }
        }
    }
    
    private static void transformTypeReferences(CtModel model) {
        List<CtTypeReference<?>> refs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> ref : refs) {
            String name = ref.getQualifiedName();
            if (name != null && name.startsWith("javax.validation.")) {
                String newName = name.replace("javax.validation", "jakarta.validation");
                CtTypeReference<?> newRef = ref.getFactory().createReference(newName);
                ref.replace(newRef);
                System.out.println("Transformed type: " + name + " -> " + newName);
            }
        }
    }
    
    private static void transformPackageReferences(CtModel model) {
        List<CtPackageReference> refs = model.getElements(new TypeFilter<>(CtPackageReference.class));
        for (CtPackageReference ref : refs) {
            String name = ref.getQualifiedName();
            if (name != null && name.startsWith("javax.validation")) {
                String newName = name.replace("javax.validation", "jakarta.validation");
                CtPackageReference newRef = ref.getFactory().createPackageReference();
                newRef.setSimpleName(newName);
                ref.replace(newRef);
                System.out.println("Transformed package: " + name + " -> " + newName);
            }
        }
    }
}