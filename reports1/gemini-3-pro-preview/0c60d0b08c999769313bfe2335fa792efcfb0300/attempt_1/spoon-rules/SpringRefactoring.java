package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for Spring Framework API changes.
 * 
 * Target Diff Analysis:
 * - org.springframework.http.MediaType [MODIFIED]
 * 
 * Detected Migration Pattern:
 * In newer Spring versions, `MediaType.APPLICATION_JSON_UTF8` and `APPLICATION_JSON_UTF8_VALUE`
 * are deprecated/removed in favor of `APPLICATION_JSON` and `APPLICATION_JSON_VALUE` 
 * (browsers now handle UTF-8 automatically).
 */
public class SpringRefactoring {

    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldRead<?>> {
        
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Safety Check: Ensure variable reference exists
            if (candidate.getVariable() == null) return false;

            // 2. Name Check: Look for the specific deprecated fields
            String fieldName = candidate.getVariable().getSimpleName();
            if (!"APPLICATION_JSON_UTF8".equals(fieldName) && 
                !"APPLICATION_JSON_UTF8_VALUE".equals(fieldName)) {
                return false;
            }

            // 3. Type/Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // If we can't resolve the type, or if it's not the Spring MediaType class, skip.
            // Using relaxed string matching to handle NoClasspath scenarios where full resolution might fail.
            if (declaringType == null || !declaringType.getQualifiedName().contains("org.springframework.http.MediaType")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            String oldName = fieldRead.getVariable().getSimpleName();
            
            // Logic: Remove "_UTF8" from the name
            // APPLICATION_JSON_UTF8 -> APPLICATION_JSON
            // APPLICATION_JSON_UTF8_VALUE -> APPLICATION_JSON_VALUE
            String newName = oldName.replace("_UTF8", "");

            // Update the reference name. 
            // In Spoon, modifying the reference attached to the invocation/read updates the source code.
            fieldRead.getVariable().setSimpleName(newName);

            System.out.println("Refactored " + oldName + " to " + newName + 
                               " at line " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Source Preservation
        // 1. Enable comments to ensure they aren't stripped
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new MediaTypeProcessor());

        try {
            System.out.println("Starting Spring Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}