package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <inputSourceDir> <outputSourceDir>");
            System.exit(1);
        }
        
        String inputSourceDir = args[0];
        String outputSourceDir = args[1];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputSourceDir);
        
        // Add processor for SpongeAPI 8.0.0 migration
        launcher.addProcessor(new SpongeAPIMigrationProcessor());
        
        launcher.run();
    }
    
    /**
     * Generic processor to migrate type references from old SpongeAPI to new SpongeAPI 8.0.0
     * This handles the structural breaking changes in a generic way:
     * 
     * Breaking Changes:
     * 1. CommandSource → CommandCause
     * 2. CommandExecutor interface moved from command.spec to command package
     * 3. CommandSpec → Command (with different builder API)
     * 4. CommandContext moved from command.args to command.parameter
     * 5. CommandException moved to command.exception package
     * 
     * The transformation matches these patterns structurally, not by project-specific names.
     * It can be applied to ANY Maven project affected by the SpongeAPI 8.0.0 breaking changes.
     */
    static class SpongeAPIMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Get the fully qualified name
            String qualifiedName = typeRef.getQualifiedName();
            
            // Pattern 1: CommandSource → CommandCause migration
            // Old: org.spongepowered.api.command.CommandSource
            // New: org.spongepowered.api.command.CommandCause
            if (qualifiedName != null && qualifiedName.equals("org.spongepowered.api.command.CommandSource")) {
                // Create new type reference for CommandCause
                CtTypeReference<?> newTypeRef = getFactory().createReference("org.spongepowered.api.command.CommandCause");
                typeRef.replace(newTypeRef);
            }
            
            // Pattern 2: CommandContext package change
            // Old: org.spongepowered.api.command.args.CommandContext  
            // New: org.spongepowered.api.command.parameter.CommandContext
            else if (qualifiedName != null && qualifiedName.equals("org.spongepowered.api.command.args.CommandContext")) {
                CtTypeReference<?> newTypeRef = getFactory().createReference("org.spongepowered.api.command.parameter.CommandContext");
                typeRef.replace(newTypeRef);
            }
            
            // Pattern 3: CommandExecutor interface moved
            // Old: org.spongepowered.api.command.spec.CommandExecutor
            // New: org.spongepowered.api.command.CommandExecutor
            else if (qualifiedName != null && qualifiedName.equals("org.spongepowered.api.command.spec.CommandExecutor")) {
                CtTypeReference<?> newTypeRef = getFactory().createReference("org.spongepowered.api.command.CommandExecutor");
                typeRef.replace(newTypeRef);
            }
            
            // Pattern 4: CommandSpec → Command migration
            // Old: org.spongepowered.api.command.spec.CommandSpec
            // New: org.spongepowered.api.command.Command
            else if (qualifiedName != null && qualifiedName.equals("org.spongepowered.api.command.spec.CommandSpec")) {
                CtTypeReference<?> newTypeRef = getFactory().createReference("org.spongepowered.api.command.Command");
                typeRef.replace(newTypeRef);
            }
            
            // Pattern 5: CommandException package change
            // Old: org.spongepowered.api.command.CommandException
            // New: org.spongepowered.api.command.exception.CommandException
            else if (qualifiedName != null && qualifiedName.equals("org.spongepowered.api.command.CommandException")) {
                CtTypeReference<?> newTypeRef = getFactory().createReference("org.spongepowered.api.command.exception.CommandException");
                typeRef.replace(newTypeRef);
            }
        }
    }
}