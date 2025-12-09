package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class SpringMvcRefactoring {

    /**
     * Processor to handle the removal of Suffix Pattern Matching configuration methods
     * in RequestMappingHandlerMapping (removed in Spring 6.0).
     */
    public static class HandlerMappingRemovalProcessor extends AbstractProcessor<CtInvocation<?>> {

        // Methods removed in Spring 6.0's RequestMappingHandlerMapping hierarchy
        private static final Set<String> REMOVED_METHODS = Set.of(
            "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch"
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            CtExecutableReference<?> executable = candidate.getExecutable();
            if (executable == null) return false;
            
            String methodName = executable.getSimpleName();
            if (!REMOVED_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                // Check for exact Spring class or generic <unknown> type in NoClasspath mode
                // Also matches AbstractHandlerMethodMapping as methods were defined there
                boolean isHandlerMapping = typeName.contains("RequestMappingHandlerMapping") 
                                        || typeName.contains("AbstractHandlerMethodMapping")
                                        || typeName.equals("<unknown>");
                
                if (!isHandlerMapping) {
                    return false;
                }
            }

            // 3. Argument Count Check (These setters take exactly 1 boolean arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Defensive check for parent
            CtElement parent = invocation.getParent();
            if (parent == null) return;

            // Log the action
            System.out.println("Refactoring: Removing removed method call '" 
                + invocation.getExecutable().getSignature() 
                + "' at line " + invocation.getPosition().getLine());

            // Action: Delete the invocation.
            // Since these methods return void, they are typically statements in a block.
            // Spoon's delete() handles removing the statement and associated whitespace/semicolons 
            // when using Sniper printer.
            invocation.delete();
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new HandlerMappingRemovalProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}