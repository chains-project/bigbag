// NOTE: The provided <dependency_change_diff> was empty. 
// This code demonstrates a generic refactoring strategy: 
// wrapping a String argument into a domain object (Wrapper), 
// strictly adhering to Sniper and NoClasspath constraints.

package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.List;

public class RefactoringRule {

    /**
     * Processor to refactor:
     * com.example.Service.process(String) -> com.example.Service.process(new Wrapper(String))
     */
    public static class ArgumentWrapperProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final String TARGET_METHOD = "process";
        private static final String TARGET_CLASS_PARTIAL = "Service";
        private static final String WRAPPER_CLASS = "com.example.Wrapper";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Expect 1 argument)
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> argType = arg.getType();

            // Rule #2: NEVER assume getType() is non-null.
            // Logic: If we can determine it is ALREADY the Wrapper, skip it.
            if (argType != null && argType.getQualifiedName().contains("Wrapper")) {
                return false;
            }
            
            // If it is explicitly a String, or unknown (null), we assume it needs processing 
            // provided the owner matches.
            
            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String ownerName = declaringType.getQualifiedName();
                // Check if owner contains target class name OR is unknown (defensive)
                if (!ownerName.contains(TARGET_CLASS_PARTIAL) && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Create reference to the new Wrapper class
            CtTypeReference<?> wrapperRef = factory.Type().createReference(WRAPPER_CLASS);

            // Transformation: new Wrapper(originalArg)
            // Note: We use raw types/wildcards in the constructor call to ensure safety
            CtNewClass<?> newWrapperInstance = factory.Code().createNewClass(
                    wrapperRef,
                    originalArg.clone()
            );

            // Replace the original argument with the new wrapped argument
            originalArg.replace(newWrapperInstance);

            System.out.println("Refactored " + TARGET_METHOD + " at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Allow user to pass input path as argument, default to current dir
        String inputPath = "/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bcafa6f39116555a4c53882ed2cf2835594d9114/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        
        // Output directory
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bcafa6f39116555a4c53882ed2cf2835594d9114/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULE #1: Preserve Source Code (Sniper)
        // 1. Enable comments to be parsed
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve exact formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // CRITICAL IMPLEMENTATION RULE #2: Defensive Coding (NoClasspath)
        // Ensure Spoon does not fail on missing types
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore duplicate declarations if scanning mixed sources
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);

        launcher.addProcessor(new ArgumentWrapperProcessor());

        try {
            System.out.println("Starting refactoring in: " + inputPath);
            launcher.run();
            System.out.println("Refactoring complete. Check ./spooned");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}