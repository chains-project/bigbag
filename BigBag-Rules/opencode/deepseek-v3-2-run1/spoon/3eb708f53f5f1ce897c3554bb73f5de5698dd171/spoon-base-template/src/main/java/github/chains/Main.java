package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

public class Main {
    
    // Map old v2 API classes to new v3 API classes
    private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
    
    static {
        // Map specific class name changes
        CLASS_MAPPINGS.put("com.google.api.services.translate.model.TranslationsResource", 
                          "com.google.api.services.translate.v3.model.Translation");
        CLASS_MAPPINGS.put("com.google.api.services.translate.model.LanguagesResource", 
                          "com.google.api.services.translate.v3.model.SupportedLanguage");
        CLASS_MAPPINGS.put("com.google.api.services.translate.model.DetectionsResourceItems", 
                          "com.google.api.services.translate.v3.model.DetectedLanguage");
        
        // Map Translate class (v2 -> v3)
        CLASS_MAPPINGS.put("com.google.api.services.translate.Translate", 
                          "com.google.api.services.translate.v3.Translate");
        
        // Generic mapping for other classes in the translate package
        CLASS_MAPPINGS.put("com.google.api.services.translate.model.", 
                          "com.google.api.services.translate.v3.model.");
        CLASS_MAPPINGS.put("com.google.api.services.translate.", 
                          "com.google.api.services.translate.v3.");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base-1.0-SNAPSHOT.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Get the factory for creating new references
        var factory = launcher.getFactory();
        
        // Process all type references
        var typeRefs = Query.getElements(model.getRootPackage(), new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String typeName = typeRef.getQualifiedName();
                if (typeName == null) {
                    return false;
                }
                
                // Check if this type reference needs updating
                for (String oldPattern : CLASS_MAPPINGS.keySet()) {
                    if (typeName.contains(oldPattern)) {
                        return true;
                    }
                }
                return false;
            }
        });
        
        // Update all matching type references
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> entry : CLASS_MAPPINGS.entrySet()) {
                String oldPattern = entry.getKey();
                String newReplacement = entry.getValue();
                
                if (typeName.contains(oldPattern)) {
                    // Handle specific class mappings
                    if (CLASS_MAPPINGS.containsKey(typeName)) {
                        String newClass = CLASS_MAPPINGS.get(typeName);
                        // Create new type reference and replace
                        CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                        typeRef.replace(newRef);
                    }
                    // Handle generic package mapping
                    else if (oldPattern.endsWith(".") && typeName.startsWith(oldPattern)) {
                        String oldClass = typeName.substring(oldPattern.length());
                        String newClass = newReplacement + oldClass;
                        // Remove "Resource" suffix if present
                        if (newClass.endsWith("Resource")) {
                            newClass = newClass.substring(0, newClass.length() - 8);
                        }
                        // Create new type reference and replace
                        CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                        typeRef.replace(newRef);
                    }
                }
            }
        }
        
        // Process all imports
        var imports = Query.getElements(model.getRootPackage(), new TypeFilter<CtImport>(CtImport.class));
        for (CtImport ctImport : imports) {
            String importStr = ctImport.toString();
            
            for (Map.Entry<String, String> entry : CLASS_MAPPINGS.entrySet()) {
                String oldPattern = entry.getKey();
                String newReplacement = entry.getValue();
                
                if (importStr.contains(oldPattern)) {
                    // Handle specific class mappings
                    if (CLASS_MAPPINGS.containsKey(importStr.trim())) {
                        String newClass = CLASS_MAPPINGS.get(importStr.trim());
                        // Replace the import
                        CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                        ctImport.setReference(newRef);
                    } 
                    // Handle generic package mapping - need to parse the import
                    else if (oldPattern.endsWith(".") && importStr.contains(oldPattern)) {
                        // Parse the fully qualified name from import
                        String importText = ctImport.getReference().toString();
                        if (importText.startsWith(oldPattern)) {
                            String oldClass = importText.substring(oldPattern.length());
                            String newClass = newReplacement + oldClass;
                            // Remove "Resource" suffix if present
                            if (newClass.endsWith("Resource")) {
                                newClass = newClass.substring(0, newClass.length() - 8);
                            }
                            // Replace the import
                            CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                            ctImport.setReference(newRef);
                        }
                    }
                }
            }
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
}