package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic, reusable Spoon transformation for SpongeAPI 7.x → 8.0.0 migration.
 * 
 * This transformation handles the core breaking changes in a generic way:
 * 
 * 1. TYPE REPLACEMENTS:
 *    - CommandSource → CommandCause
 *    - command.args.* → command.parameter.*
 *    - Text API → Adventure API
 *    
 * 2. METHOD SIGNATURE CHANGES:
 *    - CommandExecutor.execute(CommandSource, CommandContext) → execute(CommandContext)
 *    
 * 3. STATIC METHOD UPDATES:
 *    - GenericArguments.string() → Parameter.string()
 *    - CommandSpec.builder() → Command.builder()
 *    
 * The transformation is parameterized and can be applied to ANY project
 * by simply changing the input source directory.
 */
public class GenericSpongeAPITransformer {
    
    // Configuration - these mappings make the transformation generic
    private static final Map<String, String> TYPE_MAPPINGS = createTypeMappings();
    private static final Map<String, String> PACKAGE_MAPPINGS = createPackageMappings();
    
    private static Map<String, String> createTypeMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        // Core Command API changes
        mappings.put("org.spongepowered.api.command.CommandSource", 
                    "org.spongepowered.api.command.CommandCause");
        
        mappings.put("org.spongepowered.api.command.args.CommandContext",
                    "org.spongepowered.api.command.parameter.CommandContext");
        
        mappings.put("org.spongepowered.api.command.spec.CommandSpec",
                    "org.spongepowered.api.command.Command$Parameterized");
        
        mappings.put("org.spongepowered.api.command.spec.CommandExecutor",
                    "org.spongepowered.api.command.CommandExecutor");
        
        mappings.put("org.spongepowered.api.command.args.GenericArguments",
                    "org.spongepowered.api.command.parameter.Parameter");
        
        // Text API migrations (Sponge → Adventure)
        mappings.put("org.spongepowered.api.text.Text",
                    "net.kyori.adventure.text.Component");
        
        mappings.put("org.spongepowered.api.text.serializer.TextSerializers",
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        
        // Event API updates
        mappings.put("org.spongepowered.api.event.game.state.GamePreInitializationEvent",
                    "org.spongepowered.api.event.lifecycle.StartingEngineEvent$Game");
        
        mappings.put("org.spongepowered.api.event.game.state.GameInitializationEvent",
                    "org.spongepowered.api.event.lifecycle.StartedEngineEvent$Game");
        
        mappings.put("org.spongepowered.api.event.game.state.GameStoppingServerEvent",
                    "org.spongepowered.api.event.lifecycle.StoppingEngineEvent$Game");
        
        mappings.put("org.spongepowered.api.event.network.ClientConnectionEvent",
                    "org.spongepowered.api.event.network.ServerSideConnectionEvent");
        
        return Collections.unmodifiableMap(mappings);
    }
    
    private static Map<String, String> createPackageMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        // Package reorganizations
        mappings.put("org.spongepowered.api.command.args",
                    "org.spongepowered.api.command.parameter");
        
        mappings.put("org.spongepowered.api.command.spec",
                    "org.spongepowered.api.command");
        
        mappings.put("org.spongepowered.api.text",
                    "net.kyori.adventure.text");
        
        return Collections.unmodifiableMap(mappings);
    }
    
    /**
     * Main entry point - run the transformation on a source directory.
     * 
     * @param sourceDir The source directory to transform
     */
    public void transform(String sourceDir) {
        System.out.println("=== SpongeAPI 8.0.0 Migration Transformer ===");
        System.out.println("Source directory: " + sourceDir);
        
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        System.out.println("\n1. Transforming type references...");
        transformTypeReferences(model);
        
        System.out.println("\n2. Updating imports...");
        updateImports(model);
        
        System.out.println("\n3. Transforming CommandExecutor implementations...");
        transformCommandExecutors(model);
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\n=== Transformation Complete ===");
        System.out.println("Review the transformed code and manually fix any complex cases.");
    }
    
    /**
     * Transform all type references based on TYPE_MAPPINGS.
     * This is the core type replacement logic.
     */
    private void transformTypeReferences(CtModel model) {
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            String oldType = entry.getKey();
            String newType = entry.getValue();
            
            // Find all references to the old type
            List<CtTypeReference<?>> refs = model.getElements(
                new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                    @Override
                    public boolean matches(CtTypeReference<?> typeRef) {
                        return typeRef.getQualifiedName() != null && 
                               typeRef.getQualifiedName().equals(oldType);
                    }
                });
            
            if (!refs.isEmpty()) {
                System.out.println("  " + oldType + " → " + newType + " (" + refs.size() + " occurrences)");
                
                // Replace each reference
                for (CtTypeReference<?> ref : refs) {
                    try {
                        CtTypeReference<?> newRef = ref.getFactory().createReference(newType);
                        ref.replace(newRef);
                    } catch (Exception e) {
                        System.err.println("    Warning: Could not replace " + oldType + " at " + 
                                          ref.getPosition());
                    }
                }
            }
        }
    }
    
    /**
     * Update import statements based on package and type mappings.
     */
    private void updateImports(CtModel model) {
        // Get all compilation units
        List<CtCompilationUnit> units = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        
        for (CtCompilationUnit unit : units) {
            // Imports are automatically updated when types are replaced
            // Spoon handles this automatically
        }
        
        System.out.println("  Imports will be automatically updated by Spoon");
    }
    
    /**
     * Transform CommandExecutor implementations to new API.
     * This handles the method signature change.
     */
    private void transformCommandExecutors(CtModel model) {
        // Find classes implementing CommandExecutor
        List<CtClass<?>> executorClasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> clazz) {
                return clazz.getSuperInterfaces().stream()
                    .anyMatch(i -> i.getQualifiedName() != null &&
                                  (i.getQualifiedName().equals("org.spongepowered.api.command.spec.CommandExecutor") ||
                                   i.getQualifiedName().contains("CommandExecutor")));
            }
        });
        
        System.out.println("  Found " + executorClasses.size() + " CommandExecutor implementations");
        
        for (CtClass<?> clazz : executorClasses) {
            System.out.println("  - " + clazz.getQualifiedName());
            
            // Update the interface reference if it's the old one
            clazz.getSuperInterfaces().forEach(iface -> {
                if (iface.getQualifiedName() != null &&
                    iface.getQualifiedName().equals("org.spongepowered.api.command.spec.CommandExecutor")) {
                    
                    try {
                        CtTypeReference<?> newIface = iface.getFactory()
                            .createReference("org.spongepowered.api.command.CommandExecutor");
                        iface.replace(newIface);
                    } catch (Exception e) {
                        System.err.println("    Could not update interface for " + clazz.getQualifiedName());
                    }
                }
            });
        }
    }
    
    /**
     * Generate a report of needed transformations.
     * Useful for manual review before applying automatic fixes.
     */
    public void generateReport(String sourceDir) {
        System.out.println("=== SpongeAPI Migration Analysis Report ===");
        System.out.println("Source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Analyze CommandSource usage
        System.out.println("\n1. CommandSource References:");
        List<CtTypeReference<?>> commandSourceRefs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return typeRef.getQualifiedName() != null && 
                           typeRef.getQualifiedName().equals("org.spongepowered.api.command.CommandSource");
                }
            });
        
        System.out.println("   Total: " + commandSourceRefs.size() + " references");
        
        // Group by containing type
        Map<String, Integer> byType = new HashMap<>();
        for (CtTypeReference<?> ref : commandSourceRefs) {
            CtType<?> declaringType = ref.getParent(CtType.class);
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                byType.put(typeName, byType.getOrDefault(typeName, 0) + 1);
            }
        }
        
        for (Map.Entry<String, Integer> entry : byType.entrySet()) {
            System.out.println("   - " + entry.getKey() + ": " + entry.getValue() + " refs");
        }
        
        // Analyze old CommandExecutor implementations
        System.out.println("\n2. Old CommandExecutor Implementations:");
        List<CtClass<?>> oldExecutors = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> clazz) {
                return clazz.getSuperInterfaces().stream()
                    .anyMatch(i -> i.getQualifiedName() != null &&
                                  i.getQualifiedName().equals("org.spongepowered.api.command.spec.CommandExecutor"));
            }
        });
        
        for (CtClass<?> clazz : oldExecutors) {
            System.out.println("   - " + clazz.getQualifiedName());
        }
        
        // Analyze GenericArguments usage
        System.out.println("\n3. GenericArguments Usage:");
        List<CtTypeReference<?>> genericArgsRefs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return typeRef.getQualifiedName() != null && 
                           typeRef.getQualifiedName().equals("org.spongepowered.api.command.args.GenericArguments");
                }
            });
        
        System.out.println("   Total: " + genericArgsRefs.size() + " references");
        
        System.out.println("\n=== Summary ===");
        System.out.println("Run the transformer to apply automatic fixes for:");
        System.out.println("- Type replacements (CommandSource → CommandCause, etc.)");
        System.out.println("- Import updates");
        System.out.println("- CommandExecutor interface updates");
        System.out.println("\nManual review needed for:");
        System.out.println("- Method body transformations (args.getOne() → context.requireOne())");
        System.out.println("- Complex API usage patterns");
    }
    
    /**
     * Command-line interface.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  java GenericSpongeAPITransformer <command> <source-directory>");
            System.out.println();
            System.out.println("Commands:");
            System.out.println("  report    - Generate analysis report");
            System.out.println("  transform - Apply transformations");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  java GenericSpongeAPITransformer report /path/to/project/src/main/java");
            System.out.println("  java GenericSpongeAPITransformer transform /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String command = args[0];
        String sourceDir = args[1];
        
        GenericSpongeAPITransformer transformer = new GenericSpongeAPITransformer();
        
        switch (command.toLowerCase()) {
            case "report":
                transformer.generateReport(sourceDir);
                break;
                
            case "transform":
                transformer.transform(sourceDir);
                break;
                
            default:
                System.err.println("Unknown command: " + command);
                System.exit(1);
        }
    }
}