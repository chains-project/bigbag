package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MediaTypeRefactoring {

    /**
     * Processor to handle changes in org.springframework.http.MediaType.
     * Specifically targets the modernization/removal of UTF-8 specific constants
     * (e.g., APPLICATION_JSON_UTF8_VALUE) which are often deprecated or removed 
     * in favor of the standard constants (e.g., APPLICATION_JSON_VALUE).
     */
    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldRead<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Check Field Name
            String fieldName = candidate.getVariable().getSimpleName();
            if (!"APPLICATION_JSON_UTF8".equals(fieldName) && 
                !"APPLICATION_JSON_UTF8_VALUE".equals(fieldName)) {
                return false;
            }

            // 2. Check Declaring Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // If declaring type is null or unknown, we might process it if the name is unique enough,
            // but for safety, we check if it contains "MediaType"
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains("MediaType") && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            // Logic: Remove "_UTF8" from the field name to use the modern equivalent.
            // APPLICATION_JSON_UTF8       -> APPLICATION_JSON
            // APPLICATION_JSON_UTF8_VALUE -> APPLICATION_JSON_VALUE
            
            String oldName = fieldRead.getVariable().getSimpleName();
            String newName = oldName.replace("_UTF8", "");

            // In Spoon, modifying the reference simple name updates the usage in the code
            fieldRead.getVariable().setSimpleName(newName);

            System.out.println("Refactored MediaType." + oldName + " to " + newName + 
                               " at line " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES for Spoon 11+
        // 1. Enable comments to preserve license headers and Javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new MediaTypeProcessor());

        System.out.println("Starting refactoring with Spoon...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}