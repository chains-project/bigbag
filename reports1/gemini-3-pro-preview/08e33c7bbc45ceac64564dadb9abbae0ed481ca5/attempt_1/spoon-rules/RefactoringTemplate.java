package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Template
 * Generated because the <dependency_change_diff> input was empty.
 * 
 * Implements:
 * 1. SniperJavaPrettyPrinter (Preserves formatting/comments)
 * 2. NoClasspath Defensive Checks (Safe for incomplete environments)
 */
public class RefactoringTemplate {

    // TOOD: Replace these with the actual values from your Diff
    private static final String TARGET_METHOD_NAME = "oldMethodName";
    private static final String REPLACEMENT_METHOD_NAME = "newMethodName";
    private static final String TARGET_CLASS_SUBSTRING = "TargetClassName";

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();

            // 1. Name Check
            if (!TARGET_METHOD_NAME.equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching because resolving types might fail in NoClasspath mode
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Filter if we successfully resolved the type and it doesn't match our target
                // If it's <unknown> or null, we might process it to be safe (or strict depending on preference)
                if (!qualifiedName.equals("<unknown>") && !qualifiedName.contains(TARGET_CLASS_SUBSTRING)) {
                    return false;
                }
            }

            // 3. Argument Check (Optional: Add logic here if overload resolution is needed)
            // Example: if (candidate.getArguments().size() != 1) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation Logic
            CtExecutableReference<?> currentRef = invocation.getExecutable();
            
            // Clone the reference to modify it safely
            CtExecutableReference<?> newRef = currentRef.clone();
            newRef.setSimpleName(REPLACEMENT_METHOD_NAME);
            
            // Apply the change
            invocation.setExecutable(newRef);
            
            System.out.println("Refactored " + TARGET_METHOD_NAME + " to " + REPLACEMENT_METHOD_NAME + 
                             " at " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/util/SerializerProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/util/SerializerProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed");

        // =========================================================
        // CRITICAL IMPLEMENTATION RULES
        // =========================================================
        
        // 1. NoClasspath Mode: Allows running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        // 2. Preserve Source Code (Sniper Configuration)
        // Enables comment preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Forces the SniperJavaPrettyPrinter to overwrite only changed nodes 
        // while keeping the rest of the file (indentation, whitespace) intact.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =========================================================

        launcher.addProcessor(new MethodRenameProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}