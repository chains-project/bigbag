package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic Spoon transformation rule for fixing Hazelcast 5.1 breaking API changes.
 * This transformation handles package migrations from com.hazelcast.core.* to new packages.
 */
public class Main {
    
    // Map of old fully-qualified type names to new fully-qualified type names
    private static final Map<String, String> TYPE_MIGRATIONS = new HashMap<>();
    
    static {
        // Hazelcast 5.1 API migrations
        TYPE_MIGRATIONS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        TYPE_MIGRATIONS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        TYPE_MIGRATIONS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        TYPE_MIGRATIONS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_MIGRATIONS.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent");
        TYPE_MIGRATIONS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        TYPE_MIGRATIONS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        TYPE_MIGRATIONS.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        TYPE_MIGRATIONS.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.MaxSizeConfig$MaxSizePolicy");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [output-directory]");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        try {
            System.out.println("Applying Hazelcast 5.1 API migration transformation...");
            System.out.println("Source directory: " + sourceDir);
            System.out.println("Output directory: " + outputDir);
            
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Configure launcher
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setPreserveLineNumbers(true);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Apply transformations
            applyTypeMigrations(model);
            
            // Generate transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Fixed " + TYPE_MIGRATIONS.size() + " API migrations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply all type migrations defined in TYPE_MIGRATIONS map.
     */
    private static void applyTypeMigrations(CtModel model) {
        int migrationCount = 0;
        
        // Find all type references in the model
        List<CtTypeReference<?>> allTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName() != null;
            }
        });
        
        // Apply migrations to collected type references
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if this type needs migration
            for (Map.Entry<String, String> migration : TYPE_MIGRATIONS.entrySet()) {
                String oldType = migration.getKey();
                String newType = migration.getValue();
                
                if (qualifiedName.equals(oldType)) {
                    // Replace the type reference
                    CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newType);
                    
                    // Preserve type arguments if present
                    if (!typeRef.getActualTypeArguments().isEmpty()) {
                        newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                    }
                    
                    typeRef.replace(newTypeRef);
                    
                    migrationCount++;
                    System.out.println("Migrated: " + oldType + " -> " + newType);
                    break;
                }
            }
        }
        
        System.out.println("Total type references migrated: " + migrationCount);
    }
}