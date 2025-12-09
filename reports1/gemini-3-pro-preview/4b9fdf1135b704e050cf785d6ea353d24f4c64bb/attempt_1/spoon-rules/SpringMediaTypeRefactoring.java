package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringMediaTypeRefactoring {

    /**
     * Processor to handle the migration of deprecated MediaType constants.
     * 
     * Changes:
     * - MediaType.APPLICATION_JSON_UTF8_VALUE -> MediaType.APPLICATION_JSON_VALUE
     * - MediaType.APPLICATION_JSON_UTF8       -> MediaType.APPLICATION_JSON
     */
    public static class MediaTypeProcessor extends AbstractProcessor<CtFieldAccess<?>> {
        
        @Override
        public boolean isToBeProcessed(CtFieldAccess<?> candidate) {
            // 1. Get the field reference being accessed
            CtFieldReference<?> fieldRef = candidate.getVariable();
            if (fieldRef == null) return false;

            // 2. Check the Field Name
            String fieldName = fieldRef.getSimpleName();
            if (!"APPLICATION_JSON_UTF8_VALUE".equals(fieldName) && 
                !"APPLICATION_JSON_UTF8".equals(fieldName)) {
                return false;
            }

            // 3. Check the Declaring Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            
            // If unknown (null), we might be aggressive and change it if the name matches exactly,
            // but for safety, we check if we can verify the class name.
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Check if it's the Spring MediaType class
                if (!qualifiedName.contains("org.springframework.http.MediaType") && 
                    !qualifiedName.equals("MediaType")) {
                    return false;
                }
            } else {
                // In NoClasspath, declaring type might be missing. 
                // If imports are not resolved, we rely on the variable name being highly specific.
                // However, strict mode prefers skipping if we aren't sure.
                // Here we proceed if the target is implicitly static access on a type named MediaType
                if (candidate.getTarget() != null && 
                    !candidate.getTarget().toString().contains("MediaType")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtFieldAccess<?> fieldAccess) {
            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            String oldName = fieldRef.getSimpleName();
            
            // Transformation: Remove "_UTF8" from the name
            // APPLICATION_JSON_UTF8_VALUE -> APPLICATION_JSON_VALUE
            // APPLICATION_JSON_UTF8       -> APPLICATION_JSON
            String newName = oldName.replace("_UTF8", "");
            
            // We modify the reference directly, which updates the usage in the AST
            fieldRef.setSimpleName(newName);
            
            System.out.println("Refactored " + oldName + " to " + newName + 
                " at line " + fieldAccess.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed");

        // ========================================================================
        // CRITICAL CONFIGURATION: PRESERVE FORMATTING (SNIPER MODE)
        // ========================================================================
        
        // 1. Enable comments so they are not stripped
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable NoClasspath mode (Defensive coding assumed in Processor)
        launcher.getEnvironment().setNoClasspath(true);
        
        // 3. Force Sniper Printer manually to preserve original indentation/formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // ========================================================================

        launcher.addProcessor(new MediaTypeProcessor());
        
        try {
            System.out.println("Starting Spring MediaType Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}