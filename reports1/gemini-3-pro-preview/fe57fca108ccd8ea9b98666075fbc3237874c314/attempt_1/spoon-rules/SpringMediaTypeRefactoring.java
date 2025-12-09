package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringMediaTypeRefactoring {

    /**
     * Processor to handles the migration of removed MediaType constants.
     * It detects usages of APPLICATION_JSON_UTF8* and renames them to APPLICATION_JSON*.
     */
    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldRead<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // Defensive coding: Ensure variable reference exists
            CtFieldReference<?> fieldRef = candidate.getVariable();
            if (fieldRef == null) return false;

            // 1. Name Check
            String name = fieldRef.getSimpleName();
            if (!"APPLICATION_JSON_UTF8".equals(name) && !"APPLICATION_JSON_UTF8_VALUE".equals(name)) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            // We check if the field belongs to MediaType. 
            // In NoClasspath, declaringType might not be fully resolved, but qualified name usually persists.
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Match fully qualified name or simple name if imports are ambiguous
                if (!qualifiedName.contains("org.springframework.http.MediaType") && 
                    !qualifiedName.equals("MediaType")) {
                    return false;
                }
            } else {
                // If we can't determine the owner, we skip to avoid false positives
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            CtFieldReference<?> fieldRef = fieldRead.getVariable();
            String oldName = fieldRef.getSimpleName();
            
            // Transformation logic: Remove the "_UTF8" suffix
            String newName = oldName.replace("_UTF8", "");

            // Apply the rename to the reference used in this specific field read
            fieldRef.setSimpleName(newName);
            
            System.out.println("Refactored " + oldName + " to " + newName + " at line " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULE 1: Preserve Source Code (Sniper)
        // Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // Force Sniper Printer manually to preserve exact formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // CRITICAL IMPLEMENTATION RULE 2: Defensive Coding (NoClasspath)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MediaTypeProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}