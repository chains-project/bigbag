package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for Spring Cloud Stream migration.
 * 
 * Changes identified from diff:
 * - REMOVED: org.springframework.cloud.stream.annotation.EnableBinding
 * - REMOVED: org.springframework.cloud.stream.annotation.Output
 * 
 * Strategy:
 * Locate and remove usages of these annotations to resolve compilation errors 
 * related to missing classes.
 */
public class SpringStreamRefactoring {

    public static class AnnotationCleanupProcessor extends AbstractProcessor<CtAnnotation<?>> {

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            // Defensive coding: Ensure type reference exists
            CtTypeReference<?> typeRef = candidate.getAnnotationType();
            if (typeRef == null) {
                return false;
            }

            // Use qualified name to identify specific Spring Cloud Stream annotations.
            // checking .contains() allows matching even if full classpath resolution isn't perfect,
            // provided the import exists in the source.
            String qName = typeRef.getQualifiedName();
            
            // Check for EnableBinding
            if (qName.contains("org.springframework.cloud.stream.annotation.EnableBinding")) {
                return true;
            }

            // Check for Output
            // Note: "Output" is a common name, so we strictly check the package path 
            // to avoid false positives with other libraries (e.g., java.io or custom classes).
            if (qName.contains("org.springframework.cloud.stream.annotation.Output")) {
                return true;
            }

            return false;
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            
            // Action: Delete the annotation from the AST
            annotation.delete();
            
            System.out.println("Removed @" + annotationName + " at " + 
                (annotation.getPosition().isValidPosition() ? annotation.getPosition().toString() : "unknown position"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/6e9e6cdda5c5637ce66ba16b8ccfcd5ffce8141d/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/6e9e6cdda5c5637ce66ba16b8ccfcd5ffce8141d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/6e9e6cdda5c5637ce66ba16b8ccfcd5ffce8141d/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/6e9e6cdda5c5637ce66ba16b8ccfcd5ffce8141d/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve non-refactored parts accurately
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new AnnotationCleanupProcessor());

        try {
            System.out.println("Starting Spring Cloud Stream Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}