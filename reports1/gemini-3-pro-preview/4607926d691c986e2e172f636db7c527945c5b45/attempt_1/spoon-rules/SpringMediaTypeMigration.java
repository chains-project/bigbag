package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringMediaTypeMigration {

    /**
     * Processor to migrate deprecated MediaType constants.
     * Rule: Replace APPLICATION_JSON_UTF8_VALUE with APPLICATION_JSON_VALUE
     *       Replace APPLICATION_JSON_UTF8 with APPLICATION_JSON
     */
    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldRead<?>> {
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Name Check: Look for the specific deprecated fields
            String fieldName = candidate.getVariable().getSimpleName();
            if (!"APPLICATION_JSON_UTF8_VALUE".equals(fieldName) && 
                !"APPLICATION_JSON_UTF8".equals(fieldName)) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We want to ensure this belongs to MediaType, but handle cases where type resolution fails.
            
            // Check the declaring type of the variable reference if available
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // Check the target of the read (e.g., "MediaType" in "MediaType.VALUE")
            // If target is null, it might be a static import.
            if (candidate.getTarget() != null) {
                 CtTypeReference<?> targetType = candidate.getTarget().getType();
                 if (targetType != null && !targetType.getQualifiedName().contains("MediaType")) {
                     return false; // Target is explicitly something else
                 }
            } else if (declaringType != null && !declaringType.getQualifiedName().contains("MediaType")) {
                return false; // Declaring type is known and is NOT MediaType
            }
            
            // If we are here, it's either explicitly MediaType or unknown (NoClasspath) 
            // but matches the very specific variable name "APPLICATION_JSON_UTF8...".
            return true;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            String oldName = fieldRead.getVariable().getSimpleName();
            // Transformation: Remove "_UTF8" from the name
            String newName = oldName.replace("_UTF8", "");

            // Modify the reference directly. 
            // In Spoon, changing the reference name updates the usage site.
            fieldRead.getVariable().setSimpleName(newName);

            System.out.println("Refactored " + oldName + " to " + newName + 
                             " at line " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4607926d691c986e2e172f636db7c527945c5b45/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4607926d691c986e2e172f636db7c527945c5b45/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to preserve Javadoc and inline comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MediaTypeProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}