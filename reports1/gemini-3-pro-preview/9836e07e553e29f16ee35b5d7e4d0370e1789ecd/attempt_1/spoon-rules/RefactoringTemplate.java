package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Processor.
 * Generated based on generic breaking change constraints (Missing Input Diff).
 *
 * Implements:
 * 1. SniperJavaPrettyPrinter for strict source preservation.
 * 2. NoClasspath defensive checks (null-safe types).
 * 3. Generic safety (CtInvocation<?>).
 */
public class RefactoringTemplate {

    // TODO: Update these constants based on your actual Dependency Diff
    private static final String OLD_METHOD_NAME = "oldMethodName";
    private static final String NEW_METHOD_NAME = "newMethodName";
    private static final String TARGET_CLASS_PARTIAL = "TargetClassName";

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!OLD_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Context Check (Defensive for NoClasspath)
            // We use relaxed string matching because types might not fully resolve without classpath
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            
            // If owner is null (unknown) or matches our target class, we proceed.
            // If it explicitly belongs to a different known class, we skip.
            if (owner != null 
                && !owner.getQualifiedName().contains(TARGET_CLASS_PARTIAL) 
                && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Optional: Argument Type Check (Defensive)
            // Example: If you only want to refactor methods where the first arg is int
            /*
            if (candidate.getArguments().size() > 0) {
                CtTypeReference<?> argType = candidate.getArguments().get(0).getType();
                if (argType != null && !argType.getQualifiedName().equals("int")) {
                    return false;
                }
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
            
            System.out.println("Refactored " + OLD_METHOD_NAME + " to " + NEW_METHOD_NAME + 
                               " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityPostTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityPostTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for precise source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MethodRenameProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}