package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpongeRefactoring {

    /**
     * Processor to handle API breaking changes for SpongeAPI (v7 to v8 migration).
     * Handles:
     * 1. Removal of CommandSource -> Replaced with net.kyori.adventure.audience.Audience
     * 2. Relocation of Keys -> Moved from org.spongepowered.api.data.key to org.spongepowered.api.data
     */
    public static class SpongeTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive: Check for null parents or packages to avoid processing unconnected nodes
            if (candidate.getPackage() == null) {
                return false;
            }
            
            // We use string matching for NoClasspath robustness
            String qName = candidate.getQualifiedName();
            
            // Check for CommandSource (REMOVED)
            if ("org.spongepowered.api.command.CommandSource".equals(qName)) {
                return true;
            }
            
            // Check for Keys (MOVED/REMOVED old package)
            if ("org.spongepowered.api.data.key.Keys".equals(qName)) {
                return true;
            }

            return false;
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            String qName = candidate.getQualifiedName();
            CtTypeReference<?> replacement = null;

            if ("org.spongepowered.api.command.CommandSource".equals(qName)) {
                // Strategy: CommandSource was split, but 'Audience' is the standard replacement 
                // for messaging capabilities in the new API (Adventure).
                replacement = getFactory().Type().createReference("net.kyori.adventure.audience.Audience");
            } else if ("org.spongepowered.api.data.key.Keys".equals(qName)) {
                // Strategy: Update package to the new location.
                replacement = getFactory().Type().createReference("org.spongepowered.api.data.Keys");
            }

            if (replacement != null) {
                // Ensure generics are preserved if they existed (unlikely for these specific types, but good practice)
                replacement.setActualTypeArguments(candidate.getActualTypeArguments());
                
                // Replace the AST node
                candidate.replace(replacement);
                
                // Log the change
                CtElement parent = candidate.getParent();
                String position = (parent != null && parent.getPosition().isValidPosition()) 
                    ? "line " + parent.getPosition().getLine() 
                    : "unknown line";
                System.out.println("Refactored " + qName + " to " + replacement.getQualifiedName() + " at " + position);
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/SkinApplier.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/SkinApplier.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Sniper
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);
        // 4. Auto-imports help resolve the new packages (Audience/Keys) cleanly
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new SpongeTypeProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}