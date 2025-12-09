package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpongeMigration {

    /**
     * Processor to migrate usages of the removed 'org.spongepowered.api.command.CommandSource'.
     * In the SpongeAPI migration (v7 to v8), CommandSource was split. 
     * The most common type-compatible replacement for permissions/identity is 'Subject'.
     * (Note: For messaging, 'Audience' might be preferred, but 'Subject' is the direct functional ancestor).
     */
    public static class CommandSourceProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS = "org.spongepowered.api.command.CommandSource";
        private static final String NEW_CLASS = "org.spongepowered.api.service.permission.Subject";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // 1. Defensive: Check for null
            if (candidate == null) return false;

            // 2. Identify the specific type reference
            // In NoClasspath mode, we rely on string analysis rather than class loading.
            String qName = candidate.getQualifiedName();
            
            // Check for fully qualified match
            if (OLD_CLASS.equals(qName)) {
                return true;
            }
            
            // Check for simple name match (if not fully qualified in source)
            // We guard this to ensure we don't accidentally replace other "CommandSource" classes
            // by checking imports context if possible, but for NoClasspath, simple name + package hint is often required.
            if ("CommandSource".equals(candidate.getSimpleName())) {
                // If we can determine the package, great. If not, we assume it's the Sponge one 
                // given the context of the migration, but we check if it's already the new type.
                return !NEW_CLASS.equals(qName);
            }

            return false;
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create the new reference
            CtTypeReference<?> newRef = getFactory().Type().createReference(NEW_CLASS);

            // Replace the old reference with the new one
            candidate.replace(newRef);
            
            // Log for the user
            System.out.println("Refactored CommandSource to Subject at " + 
                (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/NameResolver.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/NameResolver.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed");

        // CRITICAL SETTINGS FOR PRESERVING FORMATTING
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve original code structure
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new CommandSourceProcessor());

        System.out.println("Starting Spoon Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}