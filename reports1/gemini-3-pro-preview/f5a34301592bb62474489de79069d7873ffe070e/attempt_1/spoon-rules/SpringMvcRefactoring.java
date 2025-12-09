package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class SpringMvcRefactoring {

    /**
     * Processor to handle Spring 6 breaking changes in Web MVC.
     * 1. RequestMappingInfo: patterns() -> paths()
     * 2. RequestMappingHandlerMapping: Remove setUseSuffixPatternMatch/setUseTrailingSlashMatch
     */
    public static class SpringBreakingChangesProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final Set<String> REMOVED_CONFIG_METHODS = Set.of(
            "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch",
            "setUseTrailingSlashMatch"
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();
            String methodName = executable.getSimpleName();

            // Check 1: RequestMappingInfo.Builder.patterns -> paths
            if ("patterns".equals(methodName)) {
                return isTargetType(candidate, "RequestMappingInfo");
            }

            // Check 2: RequestMappingHandlerMapping removed configuration
            if (REMOVED_CONFIG_METHODS.contains(methodName)) {
                return isTargetType(candidate, "RequestMappingHandlerMapping") || 
                       isTargetType(candidate, "AbstractHandlerMethodMapping");
            }

            return false;
        }

        private boolean isTargetType(CtInvocation<?> candidate, String typeNameFragment) {
            // Defensive Check: Target owner might be implicit or unknown in NoClasspath
            CtExecutableReference<?> exec = candidate.getExecutable();
            CtTypeReference<?> declaringType = exec.getDeclaringType();
            
            if (declaringType != null && declaringType.getQualifiedName().contains(typeNameFragment)) {
                return true;
            }
            
            // Fallback: Check variable type if available
            if (candidate.getTarget() != null && candidate.getTarget().getType() != null) {
                return candidate.getTarget().getType().getQualifiedName().contains(typeNameFragment);
            }
            
            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();

            if ("patterns".equals(methodName)) {
                processPatternsRename(invocation);
            } else if (REMOVED_CONFIG_METHODS.contains(methodName)) {
                processConfigurationRemoval(invocation);
            }
        }

        /**
         * Refactoring Rule: RequestMappingInfo.Builder.patterns(...) -> paths(...)
         */
        private void processPatternsRename(CtInvocation<?> invocation) {
            // Clone the invocation to modify it safely
            CtExecutableReference<?> oldRef = invocation.getExecutable();
            
            // Create a reference to the "paths" method
            CtExecutableReference<Object> newRef = getFactory().Method().createReference(
                oldRef.getDeclaringType(),
                oldRef.getType(),
                "paths",
                oldRef.getParameterTypes().toArray(new CtTypeReference[0])
            );

            invocation.setExecutable(newRef);
            System.out.println("[REFAC] Renamed .patterns() to .paths() at line " + invocation.getPosition().getLine());
        }

        /**
         * Refactoring Rule: Remove setUseSuffixPatternMatch(...) and add warning comment.
         */
        private void processConfigurationRemoval(CtInvocation<?> invocation) {
            // We can only safely remove statements. If it's part of an expression chain that isn't the root statement,
            // we have to be careful. Usually, these are void configuration calls.
            CtElement parent = invocation.getParent();
            
            String warningMsg = "// [Spoon-Migration] REMOVED: " + invocation.getExecutable().getSimpleName() 
                              + " is removed in Spring 6 (Strict matching enforced).";

            CtCodeSnippetStatement commentStmt = getFactory().Code().createCodeSnippetStatement(warningMsg);

            // Replace the invocation with the comment
            try {
                invocation.replace(commentStmt);
                System.out.println("[REFAC] Removed deprecated config method at line " + invocation.getPosition().getLine());
            } catch (Exception e) {
                System.err.println("[WARN] Could not replace invocation at line " + invocation.getPosition().getLine() 
                                 + ". It might be part of a fluent chain or expression.");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths - can be overridden by arguments or manually changed
        String inputPath = "/home/kth/Documents/last_transformer/output/f5a34301592bb62474489de79069d7873ffe070e/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5a34301592bb62474489de79069d7873ffe070e/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f5a34301592bb62474489de79069d7873ffe070e/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5a34301592bb62474489de79069d7873ffe070e/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR PRESERVING FORMATTING ---
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (robustness against missing deps)
        launcher.getEnvironment().setNoClasspath(true);
        // --------------------------------------------------------

        launcher.addProcessor(new SpringBreakingChangesProcessor());

        try {
            System.out.println("Starting Spring MVC Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}