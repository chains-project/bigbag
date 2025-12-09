package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * 
 * NOTE: The input diff was empty. This is a generic BOILERPLATE template 
 * designed to implement the critical rules (Sniper Printer, NoClasspath, Generics Safety).
 * 
 * Please fill in the `isToBeProcessed` and `process` methods with your specific logic.
 */
public class RefactoringBoilerplate {

    public static class BoilerplateProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (TODO: Replace "targetMethod" with the method name from your diff)
            if (!"targetMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // TODO: Replace "TargetClass" with the class name from your diff
            if (owner != null && !owner.getQualifiedName().contains("TargetClass") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Argument/Type Check (Defensive)
            // Example pattern for checking arguments safely in NoClasspath:
            /*
            if (!candidate.getArguments().isEmpty()) {
                CtTypeReference<?> argType = candidate.getArguments().get(0).getType();
                // If type is known (not null) and not the target type, skip
                if (argType != null && !argType.getQualifiedName().contains("ExpectedType")) {
                    return false;
                }
            }
            */
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // TODO: Implement your transformation logic here.
            
            // Example: Rename the method
            // invocation.getExecutable().setSimpleName("newMethodName");

            System.out.println("Refactored invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION RULES ---
        
        // 1. Enable comments to preserve existing code structure
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Handling types defensively in processor)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new BoilerplateProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}