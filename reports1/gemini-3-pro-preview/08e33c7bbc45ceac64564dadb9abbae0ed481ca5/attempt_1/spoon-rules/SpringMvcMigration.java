package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class SpringMvcMigration {

    /**
     * Processor to handle the removal of path matching configuration methods 
     * in RequestMappingHandlerMapping (Spring 6 / Boot 3 breaking change).
     */
    public static class RequestMappingHandlerMappingProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // Methods removed in Spring 6.0 from AbstractHandlerMethodMapping
        private static final Set<String> REMOVED_METHODS = Set.of(
            "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch",
            "setUseTrailingSlashMatch",
            "setDetectHandlerMethodsInAncestorContexts"
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!REMOVED_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Argument Check (Most of these are boolean setters)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If we can determine the type, check if it's related to HandlerMapping
            // In NoClasspath, declaringType might be null or <unknown>, or the actual class name if inferred.
            // These method names are highly specific to Spring MVC, so we permit <unknown> but filter out obvious mismatches.
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                if (!typeName.equals("<unknown>") && 
                    !typeName.contains("HandlerMapping") && 
                    !typeName.contains("RequestMappingInfo")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Get the original code to preserve it in the comment
            String originalCode = invocation.toString();
            
            // Construct the migration warning
            String commentContent = " [Spring 6 Migration] Method removed. " +
                                    "Strict path matching is now default. " +
                                    "See 'PathMatchConfigurer' if customization is needed. " +
                                    "Original: " + originalCode;

            // Create a Block comment
            CtComment migrationComment = factory.Code().createComment(commentContent, CtComment.CommentType.BLOCK);

            // Strategy: Replace the invocation statement with the comment.
            // We check if the invocation is a standalone statement or part of an expression.
            // Since these are void setters, they are usually statements.
            CtElement parent = invocation.getParent();
            
            try {
                invocation.replace(migrationComment);
                System.out.println("Refactored (Commented out) deprecated method '" + 
                    invocation.getExecutable().getSimpleName() + "' at line " + invocation.getPosition().getLine());
            } catch (Exception e) {
                // Fallback for edge cases (e.g. inside a lambda or unexpected structure)
                System.err.println("Could not replace invocation at line " + invocation.getPosition().getLine() + ": " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new RequestMappingHandlerMappingProcessor());

        System.out.println("Starting Spring MVC Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output directory.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}