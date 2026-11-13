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
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for Hazelcast 4.0 API migration.
 * This transformation handles the package reorganization from Hazelcast 3.x to 4.x.
 * 
 * Breaking Change Pattern:
 * - com.hazelcast.core.* classes moved to com.hazelcast.cluster.*
 * - com.hazelcast.core.IMap moved to com.hazelcast.map.IMap
 * - com.hazelcast.core.MapEvent moved to com.hazelcast.map.MapEvent
 * - com.hazelcast.config.MaxSizeConfig may have been removed/replaced
 */
public class Main {
    
    // Map of old fully-qualified class names to new ones
    private static final Map<String, String> CLASS_MAPPINGS = new HashMap<>();
    
    static {
        // Core to Cluster package migration
        CLASS_MAPPINGS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        CLASS_MAPPINGS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        CLASS_MAPPINGS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent");
        CLASS_MAPPINGS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        
        // Core to Map package migration
        CLASS_MAPPINGS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        CLASS_MAPPINGS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        
        // Config classes that may have changed
        CLASS_MAPPINGS.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.MaxSizePolicy");
        
        // Monitor package change
        CLASS_MAPPINGS.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hazelcast 4.0 migration transformation to: " + sourceDir);
        
        try {
            applyTransformation(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformation(String sourceDir) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Create and run the transformer
        HazelcastMigrationTransformer transformer = new HazelcastMigrationTransformer();
        model.getRootPackage().accept(transformer);
        
        // Write the transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
    
    /**
     * Transformer that scans and updates all type references matching the old Hazelcast API.
     */
    static class HazelcastMigrationTransformer extends CtScanner {
        
        @Override
        public <T> void visitCtTypeReference(CtTypeReference<T> typeRef) {
            super.visitCtTypeReference(typeRef);
            
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && CLASS_MAPPINGS.containsKey(qualifiedName)) {
                String newQualifiedName = CLASS_MAPPINGS.get(qualifiedName);
                
                // Create new type reference
                CtTypeReference<T> newTypeRef = typeRef.getFactory().Type().createReference(newQualifiedName);
                
                // Replace the type reference
                typeRef.replace(newTypeRef);
                
                System.out.println("Transformed type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        }
    }
}