package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MediaTypeRefactoring {

    /**
     * Processor to handle the migration of MediaType constants.
     * Replaces APPLICATION_JSON_UTF8 -> APPLICATION_JSON
     * Replaces APPLICATION_JSON_UTF8_VALUE -> APPLICATION_JSON_VALUE
     * 
     * We target CtFieldReference to ensure we catch:
     * 1. Qualified usages (MediaType.APPLICATION_JSON_UTF8)
     * 2. Static Imports (import static ...MediaType.APPLICATION_JSON_UTF8)
     * 3. Unqualified usages inside statically imported contexts.
     */
    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldReference<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldReference<?> candidate) {
            // 1. Name Check: Filter for the specific fields being removed/renamed
            String name = candidate.getSimpleName();
            if (!"APPLICATION_JSON_UTF8".equals(name) && !"APPLICATION_JSON_UTF8_VALUE".equals(name)) {
                return false;
            }

            // 2. Owner/Declaring Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getDeclaringType();
            
            // If declaring type is null, we can't be sure.
            if (declaringType == null) {
                return false;
            }

            // check string name to handle missing classpath resolution
            String qualifiedName = declaringType.getQualifiedName();
            if (qualifiedName == null || !qualifiedName.contains("org.springframework.http.MediaType")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldReference<?> fieldRef) {
            String oldName = fieldRef.getSimpleName();
            // Transformation: Remove "_UTF8" from the name
            String newName = oldName.replace("_UTF8", "");
            
            // Apply the change
            fieldRef.setSimpleName(newName);
            
            // Log for verification
            System.out.println("Refactored MediaType constant: " + oldName + " -> " + newName 
                + " at " + (fieldRef.getPosition().isValidPosition() ? fieldRef.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        
        // 1. Enable comments to preserve license headers and Javadoc
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Defensive coding in Processor required)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MediaTypeProcessor());
        
        try {
            System.out.println("Starting MediaType refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}