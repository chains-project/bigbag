package org.example.refactoring;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class SpringMvcRefactoring {

    /**
     * Processor to handle the removal of methods in RequestMappingHandlerMapping.
     * Specifically targeting `setUseSuffixPatternMatch` and `setUseRegisteredSuffixPatternMatch`
     * which were removed from the superclass in Spring 6.
     */
    public static class RequestMappingMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final Set<String> REMOVED_METHODS = Set.of(
            "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch"
        );

        private static final String TARGET_CLASS = "RequestMappingHandlerMapping";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!REMOVED_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Check Argument Count (Defensive: These setters typically take 1 boolean)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            // We want to ensure we aren't modifying some other class's setUseSuffixPatternMatch
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            
            // If owner is null (unknown), we proceed with caution if the method name is highly specific (which these are).
            // If owner is known, we verify it matches the target Spring class hierarchy.
            if (owner != null && !owner.getQualifiedName().equals("<unknown>")) {
                boolean isTargetType = owner.getQualifiedName().contains(TARGET_CLASS);
                // In NoClasspath, we can't easily check hierarchy, so we rely on the specific method names
                // appearing on a class with the matching simple or qualified name.
                if (!isTargetType) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Strategy: Since the functionality is removed, we cannot simply rename.
            // We replace the code with a comment to preserve the developer's intent while fixing the compilation error.
            // This forces the developer to review the path matching strategy (PathPatternParser).
            
            String originalCode = invocation.toString();
            String commentContent = String.format(
                " [Spring 6 Migration] Method '%s' was removed. " + 
                "Suffix pattern matching is no longer supported by default. " + 
                "Original: %s", 
                methodName, originalCode
            );

            // Create a Block comment
            CtComment comment = factory.Code().createComment(commentContent, CtComment.CommentType.BLOCK);
            
            // Replace the statement with the comment
            invocation.replace(comment);

            System.out.println("Refactored: Commented out removed method " + methodName + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: Sniper Configuration for Source Preservation
        // ==========================================================
        
        // 1. Enable comments to preserve existing documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to treat the AST as a precise model of the source
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new RequestMappingMigrationProcessor());

        System.out.println("Starting Spring MVC Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}