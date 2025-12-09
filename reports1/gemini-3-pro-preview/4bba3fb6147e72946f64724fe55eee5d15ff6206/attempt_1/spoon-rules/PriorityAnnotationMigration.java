package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class PriorityAnnotationMigration {

    /**
     * Processor to migrate javax.annotation.Priority to jakarta.annotation.Priority.
     * The diff indicates the removal of javax.annotation.Priority, which typically
     * implies a migration to the Jakarta EE namespace in modern dependencies.
     */
    public static class PriorityProcessor extends AbstractProcessor<CtAnnotation<?>> {

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            // 1. Get the annotation type reference
            CtTypeReference<?> typeRef = candidate.getAnnotationType();

            // 2. Defensive check for nulls (NoClasspath safety)
            if (typeRef == null) {
                return false;
            }

            // 3. check by qualified name (String comparison) to avoid classpath issues
            // We look specifically for the removed class: javax.annotation.Priority
            return "javax.annotation.Priority".equals(typeRef.getQualifiedName());
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            // 1. Create the new type reference for Jakarta
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference("jakarta.annotation.Priority");

            // 2. Replace the annotation type
            // Spoon handles import management automatically when the model is saved/printed
            annotation.setAnnotationType(newTypeRef);

            System.out.println("Migrated @Priority annotation at " 
                + (annotation.getPosition().isValidPosition() ? annotation.getPosition().toString() : "unknown position"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/4bba3fb6147e72946f64724fe55eee5d15ff6206/cdi-test/cdi-test-api/src/main/java/de/hilling/junit/cdi/annotations/GlobalTestImplementation.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4bba3fb6147e72946f64724fe55eee5d15ff6206/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4bba3fb6147e72946f64724fe55eee5d15ff6206/cdi-test/cdi-test-api/src/main/java/de/hilling/junit/cdi/annotations/GlobalTestImplementation.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4bba3fb6147e72946f64724fe55eee5d15ff6206/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new PriorityProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}