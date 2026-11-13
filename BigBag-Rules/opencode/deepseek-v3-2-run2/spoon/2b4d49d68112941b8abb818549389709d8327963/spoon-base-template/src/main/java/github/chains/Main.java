package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.compiler.Environment;
import java.util.Map;
import java.util.HashMap;

public class Main {
    // Map of old fully qualified class names to new fully qualified class names
    private static final Map<String, String> CLASS_RELOCATIONS = new HashMap<>();
    
    static {
        // Specific class relocations from Hazelcast 3.x to 4.x
        CLASS_RELOCATIONS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        CLASS_RELOCATIONS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        CLASS_RELOCATIONS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        CLASS_RELOCATIONS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        CLASS_RELOCATIONS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        CLASS_RELOCATIONS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        CLASS_RELOCATIONS.put("com.hazelcast.core.EntryListener", "com.hazelcast.map.listener.MapListener");
        
        // Note: com.hazelcast.core.MemberAttributeEvent was removed in Hazelcast 4.0
        // com.hazelcast.monitor.* package may not exist or has been renamed
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory> <output-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming Hazelcast API from 3.x to 4.x...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Configure environment
        Environment env = launcher.getEnvironment();
        env.setAutoImports(true);
        env.setNoClasspath(false);
        env.setCommentEnabled(true);
        env.setPreserveLineNumbers(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformTypeReferences(model);
        
        // Pretty print the transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformTypeReferences(CtModel model) {
        System.out.println("Transforming type references...");
        
        model.getAllTypes().forEach(ctType -> {
            // Transform type references in the type itself
            transformTypeReferencesInType(ctType);
            
            // Transform type references in all elements
            ctType.getElements(e -> e instanceof CtTypeReference).forEach(element -> {
                transformTypeReference((CtTypeReference<?>) element);
            });
        });
    }
    
    private static void transformTypeReferencesInType(CtType<?> ctType) {
        // Transform superclass and interfaces
        if (ctType.getSuperclass() != null) {
            transformTypeReference(ctType.getSuperclass());
        }
        
        ctType.getSuperInterfaces().forEach(superInterface -> {
            transformTypeReference(superInterface);
        });
    }
    
    private static void transformTypeReference(CtTypeReference<?> typeRef) {
        String qualifiedName = typeRef.getQualifiedName();
        
        // Check for class relocations
        for (Map.Entry<String, String> entry : CLASS_RELOCATIONS.entrySet()) {
            String oldClass = entry.getKey();
            String newClass = entry.getValue();
            
            if (qualifiedName.equals(oldClass) || qualifiedName.startsWith(oldClass + "<")) {
                System.out.println("Transforming type reference: " + qualifiedName + " -> " + newClass);
                
                // Create new type reference
                CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newClass);
                
                // Copy type arguments if present
                if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                    newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                }
                
                // Replace the type reference in the AST
                typeRef.replace(newTypeRef);
                break;
            }
        }
    }
}