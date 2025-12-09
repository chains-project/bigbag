package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collections;

public class SonarLintRefactoring {

    /**
     * Processor to handle breaking changes in AnalysisEngineConfiguration.
     * 1. Removes calls to `addEnabledLanguages` (feature removed).
     * 2. Replaces calls to `getEnabledLanguages` with `Collections.emptySet()`.
     */
    public static class AnalysisConfigProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            String methodName = candidate.getExecutable().getSimpleName();
            
            // Check for the specific methods removed in the diff
            if (!"addEnabledLanguages".equals(methodName) && !"getEnabledLanguages".equals(methodName)) {
                return false;
            }

            // Defensive NoClasspath check: Verify the owner class
            // We look for "AnalysisEngineConfiguration" in the qualified name.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }

            String ownerName = declaringType.getQualifiedName();
            // Matches "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration"
            // and inner class "...AnalysisEngineConfiguration$Builder" (or .Builder)
            return ownerName.contains("AnalysisEngineConfiguration");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();

            if ("addEnabledLanguages".equals(methodName)) {
                removeFluentInvocation(invocation);
            } else if ("getEnabledLanguages".equals(methodName)) {
                replaceGetterWithEmptySet(invocation);
            }
        }

        /**
         * Removes a method call from a fluent API chain.
         * Example: builder.setA().addEnabledLanguages(l).build()
         * Becomes: builder.setA().build()
         */
        private void removeFluentInvocation(CtInvocation<?> invocation) {
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                // Replace the invocation with the expression it was called on.
                // This effectively "unlinks" this specific method call from the chain.
                invocation.replace(target);
                System.out.println("Refactoring: Removed 'addEnabledLanguages' at line " + 
                    (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?"));
            } else {
                // Edge case: Static import or weird context (unlikely for this builder), just delete.
                invocation.delete();
            }
        }

        /**
         * Replaces a getter call with Collections.emptySet().
         * Example: config.getEnabledLanguages()
         * Becomes: java.util.Collections.emptySet()
         */
        private void replaceGetterWithEmptySet(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create reference to java.util.Collections
            CtTypeReference<Collections> collectionsRef = factory.Type().createReference(Collections.class);
            
            // Create invocation: Collections.emptySet()
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(collectionsRef),
                factory.Method().createReference(
                    collectionsRef,
                    factory.Type().createReference("java.util.Set"),
                    "emptySet"
                )
            );

            invocation.replace(replacement);
            System.out.println("Refactoring: Replaced 'getEnabledLanguages' at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args if needed)
        String inputPath = "/home/kth/Documents/last_transformer/output/3d29f9a6823fa68763d3148bc0353ac557f2a815/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d29f9a6823fa68763d3148bc0353ac557f2a815/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/3d29f9a6823fa68763d3148bc0353ac557f2a815/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d29f9a6823fa68763d3148bc0353ac557f2a815/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Robust Sniper Configuration for Source Preservation
        // =========================================================
        // 1. Enable comment recording
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force SniperJavaPrettyPrinter to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode handles missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AnalysisConfigProcessor());

        System.out.println("Starting Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}