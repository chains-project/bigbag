package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Template.
 * 
 * Since no specific Dependency Diff was provided in the prompt, this class 
 * implements a defensive boilerplate structure adhering to the Critical Implementation Rules:
 * 1. SniperJavaPrettyPrinter for source preservation.
 * 2. NoClasspath compatibility (defensive null checks).
 * 3. Generic safety.
 * 
 * Logic implemented: A placeholder Method Rename (oldMethod -> newMethod).
 */
public class RefactoringTemplate {

    public static class GenericRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> execRef = candidate.getExecutable();
            
            // 1. Defensive Check: Executable might be null in rare parsing errors
            if (execRef == null) return false;

            // 2. Name Check: Filter by method name
            // REPLACE "oldMethod" with the actual method name from the diff
            if (!"oldMethod".equals(execRef.getSimpleName())) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            // Use String matching instead of strict type resolution
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // REPLACE "TargetClassName" with the actual class/interface name
                if (!qualifiedName.contains("TargetClassName") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Argument Type Check (Example)
            // if (candidate.getArguments().size() != 1) return false;
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // LOGIC: Rename method
            // REPLACE with actual transformation logic
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored invocation at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---

        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable NoClasspath mode (resilient to missing libs)
        launcher.getEnvironment().setNoClasspath(true);

        // 3. Force Sniper Printer manually for strict source code preservation
        // This ensures indentation and formatting are identical to input
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Add the processor
        launcher.addProcessor(new GenericRefactoringProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}