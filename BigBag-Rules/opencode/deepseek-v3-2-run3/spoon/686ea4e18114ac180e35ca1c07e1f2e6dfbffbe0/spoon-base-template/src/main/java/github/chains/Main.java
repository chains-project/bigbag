package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtPackageReference;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Main {
    
    /**
     * Generic transformation rule for migrating from javax.ws.rs to jakarta.ws.rs
     * This handles Dropwizard 4.0.0 breaking change
     */
    public static void transformJaxRsToJakarta(String sourceDir) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        
        // Configure Spoon launcher
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Track transformed imports
        Set<String> transformedImports = new HashSet<>();
        
        // Find and transform all imports from javax.ws.rs to jakarta.ws.rs
        model.getElements(new TypeFilter<>(CtImport.class)).forEach(ctImport -> {
            String importStr = ctImport.toString();
            if (importStr.contains("javax.ws.rs")) {
                String newImport = importStr.replace("javax.ws.rs", "jakarta.ws.rs");
                ctImport.replace(factory.createImport(
                    factory.createReference(newImport.replace("import ", "").replace(";", "").trim())
                ));
                transformedImports.add(importStr);
                System.out.println("Transformed import: " + importStr + " -> " + newImport);
            }
        });
        
        // Find and transform all type references in the code
        model.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeRef -> {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName.startsWith("javax.ws.rs.")) {
                String newQualifiedName = qualifiedName.replace("javax.ws.rs.", "jakarta.ws.rs.");
                typeRef.setSimpleName(newQualifiedName.substring(newQualifiedName.lastIndexOf('.') + 1));
                // Create new package reference
                CtPackageReference pkgRef = factory.createPackageReference();
                pkgRef.setSimpleName("jakarta.ws.rs");
                typeRef.setPackage(pkgRef);
                System.out.println("Transformed type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        });
        
        // Apply transformations to source files
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed.");
        System.out.println("Total imports transformed: " + transformedImports.size());
        
        if (!transformedImports.isEmpty()) {
            System.out.println("\nTransformed imports:");
            transformedImports.forEach(System.out::println);
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File dir = new File(sourceDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying javax.ws.rs -> jakarta.ws.rs transformation to: " + sourceDir);
        System.out.println("This fixes Dropwizard 4.0.0 breaking change\n");
        
        transformJaxRsToJakarta(sourceDir);
    }
}