package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jakarta EE migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Track changes
        int importChanges = 0;
        int typeRefChanges = 0;
        
        // Map of old to new package patterns for Jakarta EE migration
        Map<String, String> packageMappings = new HashMap<>();
        packageMappings.put("javax.validation", "jakarta.validation");
        packageMappings.put("javax.annotation", "jakarta.annotation");
        packageMappings.put("javax.servlet", "jakarta.servlet");
        packageMappings.put("javax.persistence", "jakarta.persistence");
        packageMappings.put("javax.transaction", "jakarta.transaction");
        packageMappings.put("javax.ws.rs", "jakarta.ws.rs");
        packageMappings.put("javax.xml.bind", "jakarta.xml.bind");
        
        // Get the factory from the model
        var factory = model.getRootPackage().getFactory();
        
        // 1. Fix imports
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            try {
                Object importRef = ctImport.getReference();
                if (importRef instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) importRef;
                    String qualifiedName = typeRef.getQualifiedName();
                    
                    for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
                        String oldPkg = mapping.getKey();
                        String newPkg = mapping.getValue();
                        
                        if (qualifiedName.startsWith(oldPkg)) {
                            String newQualifiedName = qualifiedName.replace(oldPkg, newPkg);
                            System.out.println("Changing import: " + qualifiedName + " -> " + newQualifiedName);
                            
                            CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newQualifiedName);
                            ctImport.setReference(newTypeRef);
                            importChanges++;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not process import: " + ctImport);
            }
        }
        
        // 2. Fix type references in code
        List<CtTypeReference<?>> typeRefs = Query.getElements(factory, new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
                String oldPkg = mapping.getKey();
                String newPkg = mapping.getValue();
                
                if (typeName.startsWith(oldPkg)) {
                    String newTypeName = typeName.replace(oldPkg, newPkg);
                    System.out.println("Changing type reference: " + typeName + " -> " + newTypeName);
                    
                    CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newTypeName);
                    typeRef.replace(newTypeRef);
                    typeRefChanges++;
                    break;
                }
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Import changes: " + importChanges);
        System.out.println("  Type reference changes: " + typeRefChanges);
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nCode has been transformed for Jakarta EE migration.");
        System.out.println("Common package mappings applied:");
        for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
            System.out.println("  " + mapping.getKey() + " -> " + mapping.getValue());
        }
        System.out.println("\nNote: You may need to update your pom.xml dependencies:");
        System.out.println("  - Replace javax.validation:validation-api with jakarta.validation:jakarta.validation-api");
        System.out.println("  - Update other Jakarta EE dependencies as needed");
    }
}