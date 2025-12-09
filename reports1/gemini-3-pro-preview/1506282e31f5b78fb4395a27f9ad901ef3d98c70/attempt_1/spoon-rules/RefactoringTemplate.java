package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RefactoringTemplate {

    /**
     * Processor Implementation
     * TODO: Customize checks based on your specific API Diff.
     */
    public static class GenericRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            // Replace "targetMethod" with the actual method name from your diff
            if (!"targetMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // Example: if (candidate.getArguments().size() != 1) return false;

            // 3. Type Check (CRITICAL: Defensive for NoClasspath)
            // DO NOT assume types are resolved.
            /*
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Example: If we are migrating int -> Duration
            // If type is explicitly the NEW type, skip it (already migrated).
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }
            */

            // 4. Owner/Scope Check
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // Use contains() for string matching in NoClasspath mode
            if (owner != null && !owner.getQualifiedName().contains("TargetClassName") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // TODO: Implement transformation logic
            // Example: Wrapping an argument
            // CtExpression<?> originalArg = invocation.getArguments().get(0);
            // CtInvocation<?> replacement = ...
            // originalArg.replace(replacement);

            System.out.println("Refactored invocation at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/1506282e31f5b78fb4395a27f9ad901ef3d98c70/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1506282e31f5b78fb4395a27f9ad901ef3d98c70/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1506282e31f5b78fb4395a27f9ad901ef3d98c70/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1506282e31f5b78fb4395a27f9ad901ef3d98c70/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION START ---
        
        // 1. Enable comments to preserve license headers and Javadoc
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Mode (assume libraries are missing)
        launcher.getEnvironment().setNoClasspath(true);
        
        // --- CRITICAL CONFIGURATION END ---

        launcher.addProcessor(new GenericRefactoringProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Verified output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}