package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule.
 * Generated based on the provided dependency diff (Empty/Generic Template provided due to missing input).
 * 
 * Strategy:
 * 1. Identify breaking changes (Method Renaming/Signature change).
 * 2. Apply transformations using Spoon's generic invocation model.
 * 3. Enforce strict source preservation using SniperJavaPrettyPrinter.
 */
public class RefactoringRule {

    /**
     * Processor to handle the AST transformation.
     * Uses wildcards <?> to ensure generic safety.
     */
    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        // TODO: Update these constants based on the specific diff
        private static final String OLD_METHOD_NAME = "oldMethodName"; 
        private static final String TARGET_CLASS_PARTIAL_NAME = "ClassName"; 
        private static final String NEW_METHOD_NAME = "newMethodName";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest check first)
            if (!OLD_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Example: expecting 1 argument)
            // Adjust this based on specific signature changes
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Owner/Type Check (Defensive for NoClasspath)
            CtExpression<?> target = candidate.getTarget();
            
            // If the method is a static import or target is implicit 'this', target might be null or implicit
            if (target != null) {
                CtTypeReference<?> type = target.getType();
                // Check if type is known and matches the target class.
                // We use .contains() to handle fully qualified names without strict resolution
                if (type != null && !type.getQualifiedName().contains(TARGET_CLASS_PARTIAL_NAME)) {
                    // It's a method with the same name but on a different class
                    return false;
                }
            } else {
                // Handle static imports or implicit targets if necessary
                CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
                if (declaringType != null && !declaringType.getQualifiedName().contains(TARGET_CLASS_PARTIAL_NAME)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Example Transformation: Renaming the method
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);

            // Example Transformation: Modifying arguments (Defensive Type Checking)
            // for (CtExpression<?> arg : invocation.getArguments()) {
            //     CtTypeReference<?> argType = arg.getType();
            //     // Process only if type is unknown (null) or matches specific criteria
            //     if (argType == null || argType.getQualifiedName().contains("OldType")) {
            //          // Apply transformation logic
            //     }
            // }

            System.out.println("Refactored " + OLD_METHOD_NAME + " to " + NEW_METHOD_NAME + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden via args)
        String inputPath = "/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/13fd75e233a5cb2771a6cb186c0decaed6d6545a/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/13fd75e233a5cb2771a6cb186c0decaed6d6545a/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR SOURCE PRESERVATION ---
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive mode: Assume libraries might be missing
        launcher.getEnvironment().setNoClasspath(true);

        // --- PROCESSOR REGISTRATION ---
        launcher.addProcessor(new MethodRefactoringProcessor());

        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}