package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Template
 * Generated because no specific <dependency_change_diff> was provided.
 * This class implements the required infrastructure (Sniper printer, NoClasspath)
 * and provides a skeleton for implementing specific refactoring logic.
 */
public class ApiMigration {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            // Replace "targetMethodName" with the method name from your diff
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"targetMethodName".equals(methodName)) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // Use relaxed string matching instead of strict type resolution
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Replace "TargetClassName" with the class owning the method
                if (!qualifiedName.contains("TargetClassName") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument/Signature Check
            // Example: Filter by argument count
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 4. Advanced Type Check (Defensive)
            // Pattern: Verify arg type if known, skip if already migrated
            /*
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            if (argType != null && argType.getQualifiedName().contains("NewType")) {
                return false; // Already migrated
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Logic to transform the code goes here.
            // Example: Rename method
            // invocation.getExecutable().setSimpleName("newMethodName");
            
            System.out.println("Refactored invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/LargeImageITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/LargeImageITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES

        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually for precise source preservation
        // This prevents Spoon from reformatting unrelated code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding (NoClasspath Compatibility)
        // Allows running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new MigrationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}