package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool
 * Generated Template (Input Diff was empty)
 * 
 * Scenario Implemented:
 * - Replaces calls to "oldMethod" with "newMethod"
 * - Preserves formatting (Sniper)
 * - Handles missing classpath (Defensive checks)
 */
public class MigrationTool {

    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        // Define target to search for
        private static final String TARGET_METHOD_NAME = "oldMethod";
        private static final String REPLACEMENT_METHOD_NAME = "newMethod";
        private static final String TARGET_CLASS_KEYWORD = "TargetClassName"; // Part of the class name to match

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest check first)
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // In NoClasspath mode, declaringType might be null or incomplete.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If we can resolve the type, check if it matches our target class.
            // If declaringType is null (unknown), we might process it anyway if the name matches uniquely,
            // or return false to be safe. Here we check loose containment.
            if (declaringType != null && !declaringType.getQualifiedName().equals("<unknown>")) {
                if (!declaringType.getQualifiedName().contains(TARGET_CLASS_KEYWORD)) {
                    // It belongs to a different class, skip it
                    return false;
                }
            }

            // 3. Argument Check (Optional: Add specific arg count checks here)
            // if (candidate.getArguments().size() != 1) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Save position for logging
            String position = (invocation.getPosition().isValidPosition()) 
                ? "line " + invocation.getPosition().getLine() 
                : "unknown position";

            // LOGIC: Rename the method
            // Note: In Spoon, changing the executable reference simple name updates the call.
            invocation.getExecutable().setSimpleName(REPLACEMENT_METHOD_NAME);

            System.out.println("Refactored: " + TARGET_METHOD_NAME + " -> " + REPLACEMENT_METHOD_NAME + " at " + position);
            
            /* 
             * EXAMPLE: If you needed to wrap an argument (e.g., boxing):
             * 
             * Factory factory = getFactory();
             * CtExpression<?> oldArg = invocation.getArguments().get(0);
             * CtTypeReference<?> wrapperType = factory.Type().createReference("com.wrapper.NewType");
             * 
             * CtInvocation<?> wrappedArg = factory.Code().createInvocation(
             *     factory.Code().createTypeAccess(wrapperType),
             *     factory.Method().createReference(wrapperType, factory.Type().voidPrimitiveType(), "of"),
             *     oldArg.clone()
             * );
             * oldArg.replace(wrappedArg);
             */
        }
    }

    public static void main(String[] args) {
        // Configuration: Paths
        String inputPath = "/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java";  // Change this to your source root
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed"; // Change this to your output root

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed");

        // ===============================================================
        // CRITICAL: SNIPER MODE CONFIGURATION (Preserve formatting)
        // ===============================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject SniperJavaPrettyPrinter
        // This ensures the diff is minimal (only changes AST nodes we touched)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode (Robustness)
        // Allows running without compiling the project or having dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new MethodRefactoringProcessor());

        // Run
        System.out.println("Starting Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}