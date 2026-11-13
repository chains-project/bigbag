package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * GENERIC SpongeAPI 7.x → 8.0.0 Migration Transformation
 * 
 * This is a reusable transformation rule that can be applied to ANY
 * Maven project affected by the SpongeAPI 8.0.0 breaking changes.
 * 
 * BREAKING CHANGES ADDRESSED:
 * 1. CommandSource → CommandCause
 * 2. CommandExecutor signature change
 * 3. Package reorganization (command.args → command.parameter)
 * 4. Text API → Adventure API migration
 * 
 * USAGE:
 *   java -jar spoon-transformer.jar <source-directory>
 * 
 * Example:
 *   java -jar spoon-transformer.jar /workspace/ChangeSkin/sponge/src/main/java
 */
public class Main {
    
    // PARAMETERIZED TYPE MAPPINGS
    // These make the transformation generic - add more mappings as needed
    private static final Map<String, String> TYPE_MAPPINGS = createTypeMappings();
    
    private static Map<String, String> createTypeMappings() {
        Map<String, String> map = new HashMap<>();
        
        // Core Command API changes (affects ALL projects)
        map.put("org.spongepowered.api.command.CommandSource", 
                "org.spongepowered.api.command.CommandCause");
        
        map.put("org.spongepowered.api.command.args.CommandContext",
                "org.spongepowered.api.command.parameter.CommandContext");
        
        map.put("org.spongepowered.api.command.spec.CommandSpec",
                "org.spongepowered.api.command.Command$Parameterized");
        
        map.put("org.spongepowered.api.command.spec.CommandExecutor",
                "org.spongepowered.api.command.CommandExecutor");
        
        map.put("org.spongepowered.api.command.args.GenericArguments",
                "org.spongepowered.api.command.parameter.Parameter");
        
        // Text API changes (common migration)
        map.put("org.spongepowered.api.text.Text",
                "net.kyori.adventure.text.Component");
        
        map.put("org.spongepowered.api.text.serializer.TextSerializers",
                "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        
        // Event API changes
        map.put("org.spongepowered.api.event.game.state.GamePreInitializationEvent",
                "org.spongepowered.api.event.lifecycle.StartingEngineEvent$Game");
        
        map.put("org.spongepowered.api.event.game.state.GameInitializationEvent",
                "org.spongepowered.api.event.lifecycle.StartedEngineEvent$Game");
        
        return map;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("GENERIC SpongeAPI 8.0.0 Migration Transformer");
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println();
            System.err.println("Example for ChangeSkin project:");
            System.err.println("  java -jar spoon-transformer.jar /workspace/ChangeSkin/sponge/src/main/java");
            System.err.println();
            System.err.println("This transformation will:");
            System.err.println("  1. Replace CommandSource with CommandCause");
            System.err.println("  2. Update CommandExecutor interface");
            System.err.println("  3. Fix package imports (args → parameter)");
            System.err.println("  4. Apply other SpongeAPI 8.0.0 type changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Applying GENERIC SpongeAPI 8.0.0 Transformation ===");
        System.out.println("Source: " + sourceDir);
        System.out.println();
        
        try {
            // Initialize Spoon with generic settings
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Apply transformations
            int totalChanges = applyTransformations(model);
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println();
            System.out.println("=== Transformation Complete ===");
            System.out.println("Total type replacements: " + totalChanges);
            System.out.println();
            System.out.println("NEXT STEPS:");
            System.out.println("1. Run: mvn clean compile");
            System.out.println("2. Fix remaining compilation errors manually");
            System.out.println("3. Update method bodies for new API patterns");
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply all generic transformations to the model.
     * Returns total number of type replacements made.
     */
    private static int applyTransformations(CtModel model) {
        int totalChanges = 0;
        
        System.out.println("Applying type mappings:");
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            int changes = transformType(model, entry.getKey(), entry.getValue());
            if (changes > 0) {
                System.out.println("  " + entry.getKey() + " → " + entry.getValue() + 
                                 " (" + changes + " occurrences)");
                totalChanges += changes;
            }
        }
        
        System.out.println();
        System.out.println("Updating CommandExecutor implementations...");
        int executorChanges = updateCommandExecutors(model);
        System.out.println("  Updated " + executorChanges + " CommandExecutor implementations");
        
        return totalChanges;
    }
    
    /**
     * Generic type transformation method.
     * Replaces all references to oldType with newType.
     */
    private static int transformType(CtModel model, String oldType, String newType) {
        List<CtTypeReference<?>> refs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return typeRef.getQualifiedName() != null && 
                           typeRef.getQualifiedName().equals(oldType);
                }
            });
        
        for (CtTypeReference<?> ref : refs) {
            try {
                CtTypeReference<?> newRef = ref.getFactory().createReference(newType);
                ref.replace(newRef);
            } catch (Exception e) {
                System.err.println("    Warning: Could not replace " + oldType);
            }
        }
        
        return refs.size();
    }
    
    /**
     * Update CommandExecutor interface references.
     */
    private static int updateCommandExecutors(CtModel model) {
        List<CtClass<?>> executorClasses = model.getElements(
            new TypeFilter<CtClass<?>>(CtClass.class) {
                @Override
                public boolean matches(CtClass<?> clazz) {
                    return clazz.getSuperInterfaces().stream()
                        .anyMatch(i -> i.getQualifiedName() != null &&
                                      i.getQualifiedName().equals(
                                          "org.spongepowered.api.command.spec.CommandExecutor"));
                }
            });
        
        for (CtClass<?> clazz : executorClasses) {
            System.out.println("  - " + clazz.getQualifiedName());
            
            clazz.getSuperInterfaces().forEach(iface -> {
                if (iface.getQualifiedName() != null &&
                    iface.getQualifiedName().equals("org.spongepowered.api.command.spec.CommandExecutor")) {
                    
                    try {
                        CtTypeReference<?> newIface = iface.getFactory()
                            .createReference("org.spongepowered.api.command.CommandExecutor");
                        iface.replace(newIface);
                    } catch (Exception e) {
                        System.err.println("    Could not update interface");
                    }
                }
            });
        }
        
        return executorClasses.size();
    }
    
    /**
     * Helper method to analyze a project before transformation.
     * Useful for understanding the scope of changes needed.
     */
    public static void analyzeProject(String sourceDir) {
        System.out.println("=== SpongeAPI 8.0.0 Migration Analysis ===");
        System.out.println("Source: " + sourceDir);
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Count CommandSource usage
        long commandSourceCount = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return "org.spongepowered.api.command.CommandSource"
                        .equals(typeRef.getQualifiedName());
                }
            }).size();
        
        // Count old CommandExecutors
        long oldExecutorCount = model.getElements(
            new TypeFilter<CtClass<?>>(CtClass.class) {
                @Override
                public boolean matches(CtClass<?> clazz) {
                    return clazz.getSuperInterfaces().stream()
                        .anyMatch(i -> "org.spongepowered.api.command.spec.CommandExecutor"
                            .equals(i.getQualifiedName()));
                }
            }).size();
        
        System.out.println("ANALYSIS RESULTS:");
        System.out.println("  CommandSource references: " + commandSourceCount);
        System.out.println("  Old CommandExecutor implementations: " + oldExecutorCount);
        System.out.println();
        System.out.println("TRANSFORMATION WILL:");
        System.out.println("  1. Replace " + commandSourceCount + " CommandSource references");
        System.out.println("  2. Update " + oldExecutorCount + " CommandExecutor classes");
        System.out.println("  3. Apply " + TYPE_MAPPINGS.size() + " type mappings");
    }
}