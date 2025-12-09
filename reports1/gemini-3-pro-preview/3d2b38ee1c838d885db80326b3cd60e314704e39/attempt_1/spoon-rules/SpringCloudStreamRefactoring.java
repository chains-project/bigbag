package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringCloudStreamRefactoring {

    /**
     * Processor to handle the removal of Spring Cloud Stream annotations.
     * Based on the diff:
     * - org.springframework.cloud.stream.annotation.EnableBinding (REMOVED)
     * - org.springframework.cloud.stream.annotation.Output (REMOVED)
     * 
     * Strategy: Remove usages of these annotations as they no longer exist.
     * This prepares the codebase for migration to the functional programming model.
     */
    public static class AnnotationRemovalProcessor extends AbstractProcessor<CtAnnotation<?>> {
        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            CtTypeReference<?> typeRef = candidate.getAnnotationType();
            
            // Defensive check for NoClasspath scenarios
            if (typeRef == null) {
                return false;
            }

            String qName = typeRef.getQualifiedName();
            if (qName == null) {
                return false;
            }

            // Identify the specific removed annotations
            // We check for the full qualified name, or the simple name if resolution fails/is ambiguous
            boolean isEnableBinding = qName.equals("org.springframework.cloud.stream.annotation.EnableBinding")
                    || (qName.endsWith(".EnableBinding") && qName.contains("springframework"));
            
            boolean isOutput = qName.equals("org.springframework.cloud.stream.annotation.Output")
                    || (qName.endsWith(".Output") && qName.contains("springframework"));

            return isEnableBinding || isOutput;
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            System.out.println("Removing removed annotation: " + annotation.getAnnotationType().getQualifiedName() 
                + " at line " + annotation.getPosition().getLine());
            
            // Refactoring Strategy: Delete the annotation entirely.
            // Since the class is removed, the annotation cannot remain.
            annotation.delete();
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/3d2b38ee1c838d885db80326b3cd60e314704e39/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d2b38ee1c838d885db80326b3cd60e314704e39/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/3d2b38ee1c838d885db80326b3cd60e314704e39/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d2b38ee1c838d885db80326b3cd60e314704e39/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Code Preservation
        // 1. Enable comments to ensure they are parsed and attached
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true to handle missing library dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new AnnotationRemovalProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}