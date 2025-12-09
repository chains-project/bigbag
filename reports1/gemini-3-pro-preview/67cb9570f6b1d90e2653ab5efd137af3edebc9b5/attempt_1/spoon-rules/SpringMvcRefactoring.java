package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtComment;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringMvcRefactoring {

    /**
     * Processor to handle breaking changes in RequestMappingHandlerMapping.
     * The diff indicates "METHOD_REMOVED_IN_SUPERCLASS(MAJOR)".
     * In the context of Spring MVC, this typically refers to the removal of suffix pattern match configuration methods
     * (e.g., setUseSuffixPatternMatch, setUseRegisteredSuffixPatternMatch) which were removed in Spring 6.
     */
    public static class RequestMappingHandlerMappingProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Safety Check
            if (candidate.getExecutable() == null) return false;

            // 2. Name Check
            // We target specific configuration methods known to be removed.
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"setUseSuffixPatternMatch".equals(methodName) && 
                !"setUseRegisteredSuffixPatternMatch".equals(methodName) &&
                !"setUseTrailingSlashMatch".equals(methodName)) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If the type is unknown (NoClasspath), we rely on the specific method names (low collision risk).
            // If the type is known, we ensure it's related to RequestMappingHandlerMapping.
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                boolean isTargetClass = qualifiedName.contains("RequestMappingHandlerMapping");
                boolean isUnknown = qualifiedName.equals("<unknown>");
                
                if (!isTargetClass && !isUnknown) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Transformation: The method is removed, so we must remove the invocation.
            // To preserve developer intent and alert them, we replace it with a FIXME comment
            // rather than silently deleting the line.

            CtComment comment = factory.Code().createComment(
                "FIXME: Spring Migration - Method '" + invocation.getExecutable().getSimpleName() + 
                "' was removed. Configure path matching via PathMatchConfigurer or check migration guide.", 
                CtComment.CommentType.BLOCK
            );

            // Replace the invocation statement with the comment
            try {
                invocation.replace(comment);
                System.out.println("Refactored: Removed usage of " + invocation.getExecutable().getSimpleName() + 
                                 " at line " + invocation.getPosition().getLine());
            } catch (UnsupportedOperationException e) {
                System.err.println("Skipping immutable element at line " + invocation.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/67cb9570f6b1d90e2653ab5efd137af3edebc9b5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/67cb9570f6b1d90e2653ab5efd137af3edebc9b5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/67cb9570f6b1d90e2653ab5efd137af3edebc9b5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/67cb9570f6b1d90e2653ab5efd137af3edebc9b5/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve existing documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to ensure strict source code preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Configure NoClasspath mode for robustness
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new RequestMappingHandlerMappingProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}