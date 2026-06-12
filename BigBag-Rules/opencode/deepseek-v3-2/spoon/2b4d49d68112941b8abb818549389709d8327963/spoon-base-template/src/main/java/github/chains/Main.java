package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class Main {
    
    // Mapping from old fully-qualified class names to new ones
    private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
    
    // Classes that were removed and need special handling
    private static final Set<String> REMOVED_CLASSES = new HashSet<>();
    
    static {
        // Package reorganization from com.hazelcast.core to com.hazelcast.cluster
        CLASS_MAPPINGS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        CLASS_MAPPINGS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        CLASS_MAPPINGS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        CLASS_MAPPINGS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.InitialMembershipEvent", "com.hazelcast.cluster.InitialMembershipEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.InitialMembershipListener", "com.hazelcast.cluster.InitialMembershipListener");
        
        // Package reorganization from com.hazelcast.core to com.hazelcast.map
        CLASS_MAPPINGS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        CLASS_MAPPINGS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.EntryEvent", "com.hazelcast.map.EntryEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.EntryListener", "com.hazelcast.map.EntryListener");
        
        // Removed classes (will need manual fixes)
        REMOVED_CLASSES.add("com.hazelcast.core.MemberAttributeEvent");
        REMOVED_CLASSES.add("com.hazelcast.config.MaxSizeConfig");
        
        // com.hazelcast.monitor package was removed entirely
        // Classes from this package will need inspection and manual fixes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hazelcast 4.0 API migration to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        applyTransformations(model);
        
        // Write changes back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Note: Some classes may need manual fixes:");
        System.out.println("  - com.hazelcast.core.MemberAttributeEvent was removed");
        System.out.println("  - com.hazelcast.config.MaxSizeConfig was removed (use EvictionConfig)");
        System.out.println("  - com.hazelcast.monitor package was removed");
    }
    
    private static void applyTransformations(CtModel model) {
        // Get all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Get the compilation unit for this type
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu != null) {
                // Process imports in this compilation unit
                processImports(cu);
            }
            
            // Process type references in this type
            processTypeReferences(type);
        }
    }
    
    private static void processImports(CtCompilationUnit cu) {
        for (CtImport ctImport : cu.getImports()) {
            if (ctImport.getReference() != null) {
                String importedType = ctImport.getReference().toString();
                
                if (CLASS_MAPPINGS.containsKey(importedType)) {
                    String newType = CLASS_MAPPINGS.get(importedType);
                    System.out.println("Updating import: " + importedType + " -> " + newType);
                    
                    // Create new type reference for the import
                    CtTypeReference<?> newTypeRef = cu.getFactory().createReference(newType);
                    ctImport.setReference(newTypeRef);
                } else if (REMOVED_CLASSES.contains(importedType)) {
                    System.out.println("WARNING: Import of removed class: " + importedType + " in " + cu.getFile().getName());
                } else if (importedType.startsWith("com.hazelcast.monitor.")) {
                    System.out.println("WARNING: Import from removed package: " + importedType + " in " + cu.getFile().getName());
                }
            }
        }
    }
    
    private static void processTypeReferences(CtType<?> type) {
        type.accept(new CtScanner() {
            @Override
            public <T> void visitCtTypeReference(spoon.reflect.reference.CtTypeReference<T> reference) {
                super.visitCtTypeReference(reference);
                
                String typeName = reference.getQualifiedName();
                
                if (CLASS_MAPPINGS.containsKey(typeName)) {
                    String newType = CLASS_MAPPINGS.get(typeName);
                    System.out.println("Updating type reference: " + typeName + " -> " + newType);
                    
                    // Create new type reference
                    CtTypeReference<?> newTypeRef = reference.getFactory().createReference(newType);
                    
                    // Replace the type reference in its parent
                    try {
                        reference.replace(newTypeRef);
                    } catch (Exception e) {
                        System.err.println("Failed to replace type reference: " + typeName + " - " + e.getMessage());
                    }
                } else if (REMOVED_CLASSES.contains(typeName)) {
                    System.out.println("WARNING: Reference to removed class: " + typeName + " in " + type.getQualifiedName());
                } else if (typeName.startsWith("com.hazelcast.monitor.")) {
                    System.out.println("WARNING: Reference to removed package: " + typeName + " in " + type.getQualifiedName());
                }
            }
        });
    }
}