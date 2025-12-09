package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Strategy:
 * The provided diff indicates that `EnableBinding` and `Output` annotations have been removed 
 * from the `org.springframework.cloud.stream.annotation` package.
 * 
 * This processor scans for usages of these removed annotations and deletes them to fix 
 * compilation errors (Legacy Annotation Removal). 
 * 
 * Note: Users are expected to migrate to the Spring Cloud Function model manually, 
 * as semantic migration from @EnableBinding to functional beans is too complex for 
 * a deterministic AST transformation without deeper context. This script automates 
 * the cleanup of the removed types.
 */
public class SpringCloudStreamRefactoring {

    public static class AnnotationCleanupProcessor extends AbstractProcessor<CtAnnotation<?>> {
        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            // Defensive coding: Ensure type reference exists
            CtTypeReference<?> typeRef = candidate.getAnnotationType();
            if (typeRef == null) {
                return false;
            }

            // use getQualifiedName() combined with contains() for NoClasspath safety
            String qualifiedName = typeRef.getQualifiedName();

            // Target 1: org.springframework.cloud.stream.annotation.EnableBinding
            if (qualifiedName.contains("org.springframework.cloud.stream.annotation.EnableBinding")) {
                return true;
            }

            // Target 2: org.springframework.cloud.stream.annotation.Output
            if (qualifiedName.contains("org.springframework.cloud.stream.annotation.Output")) {
                return true;
            }

            return false;
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            // Log the modification
            System.out.println("Refactoring: Removing removed annotation @" + 
                annotation.getAnnotationType().getSimpleName() + 
                " at line " + annotation.getPosition().getLine());

            // Delete the annotation from its parent element (Method, Class, Field)
            annotation.delete();
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/2c04b9834112eba86fbb8ad1f925128d49449c41/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2c04b9834112eba86fbb8ad1f925128d49449c41/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/2c04b9834112eba86fbb8ad1f925128d49449c41/log-record/src/main/java/cn/monitor4all/logRecord/configuration/StreamSenderConfiguration.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2c04b9834112eba86fbb8ad1f925128d49449c41/attempt_1/transformed");

        // CRITICAL CONFIGURATION
        // 1. Enable comments to preserve them in the AST
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting and unrelated code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to allow running without full dependency resolution
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AnnotationCleanupProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}