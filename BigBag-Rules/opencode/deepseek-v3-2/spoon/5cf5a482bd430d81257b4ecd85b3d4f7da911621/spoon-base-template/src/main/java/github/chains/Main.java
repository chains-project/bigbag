package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.util.ModelList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

public class Main {
    // Map of common Jakarta EE package migrations from javax to jakarta
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Jakarta EE MVC
        PACKAGE_MAPPINGS.put("javax.mvc.", "jakarta.mvc.");
        // Jakarta EE REST (JAX-RS)
        PACKAGE_MAPPINGS.put("javax.ws.rs.", "jakarta.ws.rs.");
        // Jakarta EE CDI
        PACKAGE_MAPPINGS.put("javax.enterprise.", "jakarta.enterprise.");
        PACKAGE_MAPPINGS.put("javax.inject.", "jakarta.inject.");
        // Jakarta EE Annotations
        PACKAGE_MAPPINGS.put("javax.annotation.", "jakarta.annotation.");
        // Jakarta EE EJB
        PACKAGE_MAPPINGS.put("javax.ejb.", "jakarta.ejb.");
        // Jakarta EE Persistence (JPA)
        PACKAGE_MAPPINGS.put("javax.persistence.", "jakarta.persistence.");
        // Jakarta EE Validation (Bean Validation)
        PACKAGE_MAPPINGS.put("javax.validation.", "jakarta.validation.");
        // Jakarta EE Security
        PACKAGE_MAPPINGS.put("javax.security.", "jakarta.security.");
        // Jakarta EE JSON
        PACKAGE_MAPPINGS.put("javax.json.", "jakarta.json.");
        // Jakarta EE XML
        PACKAGE_MAPPINGS.put("javax.xml.bind.", "jakarta.xml.bind.");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jakarta EE migration transformation to: " + sourceDir);
        System.out.println("Migrating packages: " + PACKAGE_MAPPINGS.keySet());
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Get the factory
        spoon.reflect.factory.Factory factory = launcher.getFactory();
        
        // Track transformations
        int transformedImports = 0;
        int transformedRefs = 0;
        
        // Process each package mapping
        for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
            String oldPrefix = mapping.getKey();
            String newPrefix = mapping.getValue();
            
            System.out.println("\nProcessing migration: " + oldPrefix + " -> " + newPrefix);
            
            // Phase 1: Transform imports
            System.out.println("  Transforming imports...");
            List<CtImport> imports = Query.getElements(factory, new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport ctImport) {
                    try {
                        spoon.reflect.reference.CtReference ref = ctImport.getReference();
                        if (ref instanceof CtTypeReference) {
                            CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                            String qualName = typeRef.getQualifiedName();
                            return qualName != null && qualName.startsWith(oldPrefix);
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                }
            });
            
            for (CtImport ctImport : imports) {
                try {
                    // Get the reference from the import
                    spoon.reflect.reference.CtReference ref = ctImport.getReference();
                    if (ref instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                        String oldName = typeRef.getQualifiedName();
                        if (oldName.startsWith(oldPrefix)) {
                            String newName = oldName.replace(oldPrefix, newPrefix);
                            System.out.println("    Transforming import: " + oldName + " -> " + newName);
                            
                            // Get the compilation unit
                            CtCompilationUnit cu = ctImport.getPosition().getCompilationUnit();
                            if (cu != null) {
                                // Get imports list
                                ModelList<CtImport> importList = cu.getImports();
                                
                                // Find and replace the import
                                int index = importList.indexOf(ctImport);
                                if (index >= 0) {
                                    // Create new type reference
                                    CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                                    
                                    // Create new import
                                    CtImport newImport = factory.createImport(newTypeRef);
                                    
                                    // Replace the import
                                    importList.set(index, newImport);
                                    transformedImports++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("    Error transforming import: " + e.getMessage());
                }
            }
            
            // Phase 2: Transform type references in code
            System.out.println("  Transforming type references...");
            List<CtTypeReference<?>> typeRefs = Query.getElements(factory, new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    String qualName = typeRef.getQualifiedName();
                    return qualName != null && qualName.startsWith(oldPrefix);
                }
            });
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                try {
                    String oldName = typeRef.getQualifiedName();
                    if (oldName.startsWith(oldPrefix)) {
                        String newName = oldName.replace(oldPrefix, newPrefix);
                        
                        // Create new type reference
                        CtTypeReference<?> newTypeRef = factory.createReference(newName);
                        
                        // Replace the reference
                        typeRef.replace(newTypeRef);
                        System.out.println("    Transforming reference: " + oldName + " -> " + newName);
                        transformedRefs++;
                    }
                } catch (Exception e) {
                    System.err.println("    Error transforming reference: " + e.getMessage());
                }
            }
        }
        
        // Phase 3: Output transformed code
        System.out.println("\nPhase 3: Writing transformed code...");
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
        System.out.println("Summary:");
        System.out.println("  - Transformed " + transformedImports + " imports");
        System.out.println("  - Transformed " + transformedRefs + " type references");
        System.out.println("  - Output written to: " + sourceDir);
        System.out.println("\nNote: You may need to update your project dependencies to use Jakarta EE 9+ APIs.");
        System.out.println("      Update your pom.xml to use jakarta.jakartaee-api:9.0.0 or later.");
    }
    
    // Helper method to check if a type reference should be transformed
    private static boolean shouldTransform(String qualifiedName) {
        for (String prefix : PACKAGE_MAPPINGS.keySet()) {
            if (qualifiedName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    // Helper method to get transformed name
    private static String getTransformedName(String qualifiedName) {
        for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
            if (qualifiedName.startsWith(mapping.getKey())) {
                return qualifiedName.replace(mapping.getKey(), mapping.getValue());
            }
        }
        return qualifiedName;
    }
}