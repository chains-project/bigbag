package org.spongepowered.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpongeApiRefactoring {

    /**
     * Processor to handle the removal of org.spongepowered.api.command.CommandSource.
     * It replaces references to CommandSource with net.kyori.adventure.audience.Audience.
     * This is the standard migration path for message-sending capabilities in Sponge 8+.
     */
    public static class CommandSourceRefactoringProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private static final String OLD_CLASS = "org.spongepowered.api.command.CommandSource";
        private static final String NEW_CLASS = "net.kyori.adventure.audience.Audience";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive: Check for null
            if (candidate == null) return false;

            // Avoid processing the replacement itself if it's already there
            if (candidate.getQualifiedName().equals(NEW_CLASS)) return false;

            // Check if it matches the old class
            // In NoClasspath, getQualifiedName() relies on imports or simple names
            return OLD_CLASS.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            Factory factory = getFactory();
            
            // Create reference to the new interface
            CtTypeReference<?> audienceRef = factory.Type().createReference(NEW_CLASS);
            
            // Replace the old reference with the new one
            ref.replace(audienceRef);
            
            System.out.println("Refactored CommandSource to Audience at line " 
                + (ref.getPosition().isValidPosition() ? ref.getPosition().getLine() : "unknown"));
        }
    }

    /**
     * Processor to handle the removal of DataTranslators.UUID.
     * Since the exact replacement logic can vary (e.g., using TypeTokens or Configurate nodes),
     * this processor replaces the field access with a placeholder and a FIXME comment
     * to prevent compilation errors and alert the developer.
     */
    public static class DataTranslatorsRefactoringProcessor extends AbstractProcessor<CtFieldRead<?>> {
        
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Check Field Name
            if (!"UUID".equals(candidate.getVariable().getSimpleName())) return false;

            // 2. Check Declaring Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // If declaring type is unknown (null) or doesn't match DataTranslators, skip
            if (declaringType == null) return false;
            
            String qualifiedName = declaringType.getQualifiedName();
            return qualifiedName.contains("org.spongepowered.api.data.persistence.DataTranslators");
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            Factory factory = getFactory();
            
            // Create a code snippet that compiles (as null) but includes a visible FIXME comment
            CtExpression<?> fixme = factory.Code().createCodeSnippetExpression("/* FIXME: DataTranslators.UUID removed */ null");
            
            fieldRead.replace(fixme);
            
            System.out.println("Flagged removed DataTranslators.UUID usage at line " 
                + (fieldRead.getPosition().isValidPosition() ? fieldRead.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/SkinDownloader.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/402082609522c66a3b790aedafd0570148a7d53f/ChangeSkin/sponge/src/main/java/com/github/games647/changeskin/sponge/task/SkinDownloader.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/402082609522c66a3b790aedafd0570148a7d53f/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add processors
        launcher.addProcessor(new CommandSourceRefactoringProcessor());
        launcher.addProcessor(new DataTranslatorsRefactoringProcessor());

        try {
            System.out.println("Starting SpongeAPI Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}