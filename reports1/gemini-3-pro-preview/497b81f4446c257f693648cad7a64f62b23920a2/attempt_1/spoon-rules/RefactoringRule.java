package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RefactoringRule {

    /**
     * Processor: Handles the AST transformation.
     * Logic: Renames 'TargetClass.oldMethod()' to 'TargetClass.newMethod()'.
     * 
     * Uses generic wildcards (CtInvocation<?>) to ensure type safety in Spoon.
     */
    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Defensive Check: Ensure executable reference exists
            CtExecutableReference<?> executable = candidate.getExecutable();
            if (executable == null) return false;

            // 2. Name Check: Fast filter by method name
            if (!"oldMethod".equals(executable.getSimpleName())) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // In NoClasspath mode, types might be null or <unknown>.
            // We use loose string matching instead of strict class resolution.
            CtTypeReference<?> declaringType = executable.getDeclaringType();

            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Check if the type matches our target (relaxed check)
                // If it is <unknown>, we might process it anyway if the method name is unique enough.
                if (!qualifiedName.contains("TargetClass") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            
            // Log for debugging
            System.out.println("Refactoring method at: " + invocation.getPosition());

            // TRANSFORMATION: Rename the method
            executable.setSimpleName("newMethod");
            
            // Note: In NoClasspath mode, Spoon often modifies the reference directly.
            // If arguments needed changing (e.g., adding a parameter), we would manipulate 
            // invocation.getArguments() here.
        }
    }

    public static void main(String[] args) {
        // Default input/output paths
        String inputPath = "/home/kth/Documents/last_transformer/output/497b81f4446c257f693648cad7a64f62b23920a2/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/497b81f4446c257f693648cad7a64f62b23920a2/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/497b81f4446c257f693648cad7a64f62b23920a2/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/497b81f4446c257f693648cad7a64f62b23920a2/attempt_1/transformed");

        // =======================================================================
        // CRITICAL SETTINGS: Source Code Preservation (Sniper Mode)
        // =======================================================================
        
        // 1. Enable comments so they are not stripped during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject the SniperJavaPrettyPrinter.
        // This printer only reprints the AST nodes that were modified, 
        // strictly preserving the indentation and formatting of the rest of the file.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =======================================================================
        // CRITICAL SETTINGS: NoClasspath Compatibility
        // =======================================================================
        
        // 3. Allow running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);
        // Prevent failure on unresolved types
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        // Register the processor
        launcher.addProcessor(new MethodRenameProcessor());

        // Execute
        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Done. Modified sources are in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}