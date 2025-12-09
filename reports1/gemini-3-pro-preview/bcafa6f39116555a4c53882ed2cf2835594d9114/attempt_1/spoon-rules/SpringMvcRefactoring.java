package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class SpringMvcRefactoring {

    /**
     * Processor to remove calls to methods removed from RequestMappingHandlerMapping
     * and its superclasses (specifically legacy path matching configurations).
     */
    public static class RequestMappingHandlerMappingProcessor extends AbstractProcessor<CtInvocation<?>> {

        // The specific methods known to be removed in the major version bump detected
        private static final Set<String> REMOVED_METHODS = Set.of(
            "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch",
            "setUseTrailingSlashMatch",
            "setCheckHeaders" // Also removed from RequestMappingInfo/HandlerMapping logic
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!REMOVED_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            String ownerName = (declaringType != null) ? declaringType.getQualifiedName() : "";
            
            // In NoClasspath, types might be unknown. However, these method names are 
            // highly specific to Spring's RequestMappingHandlerMapping hierarchy.
            // We verify if the owner looks relevant or is unknown.
            boolean isRelevantOwner = ownerName.contains("RequestMappingHandlerMapping") 
                                   || ownerName.contains("AbstractHandlerMethodMapping")
                                   || ownerName.contains("RequestMappingInfo")
                                   || ownerName.equals("<unknown>");

            if (!isRelevantOwner) {
                // Secondary check: look at the variable being called upon
                if (candidate.getTarget() != null && candidate.getTarget().getType() != null) {
                    String targetType = candidate.getTarget().getType().getQualifiedName();
                    if (!targetType.contains("HandlerMapping")) {
                        return false;
                    }
                } else {
                    // If we can't determine owner or target type, but the method name is 
                    // extremely specific (like setUseSuffixPatternMatch), we assume it's a match 
                    // to prevent compilation errors.
                    return true; 
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Strategy: Remove the invocation.
            // These methods configure legacy behavior that is no longer supported.
            // Removing them reverts to the new Spring defaults.
            
            // We use delete() which safely removes the statement from the parent block.
            invocation.delete();
            
            System.out.println("Refactoring: Removed call to " + invocation.getExecutable().getSimpleName() 
                             + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bcafa6f39116555a4c53882ed2cf2835594d9114/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bcafa6f39116555a4c53882ed2cf2835594d9114/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (robustness against missing deps)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new RequestMappingHandlerMappingProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}