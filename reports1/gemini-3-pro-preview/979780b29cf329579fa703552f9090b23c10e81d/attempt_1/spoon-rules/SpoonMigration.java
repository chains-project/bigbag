package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonMigration {

    /**
     * Processor template configured for NoClasspath and Generics safety.
     * Replace "targetMethod" and transformation logic with actual Diff requirements.
     */
    public static class BreakingChangeProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            // TODO: Replace "targetMethod" with the method name from your diff
            if (!"targetMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // TODO: Adjust based on the method signature change
            if (candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath - Rule 2)
            // We look at the first argument as an example.
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // CRITICAL: Handle NULL type (inference failure in NoClasspath)
            // If type is known and does NOT match expected criteria, skip.
            // If type is null (unknown), we assume it MIGHT be a match and let it pass to 'process',
            // or apply stricter heuristics (like checking variable names).
            if (type != null) {
                // Example: If we are migrating FROM int TO Duration, ignore if already Duration
                if (type.getQualifiedName().contains("NewType")) {
                    return false;
                }
            }

            // 4. Owner/Scope Check
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            // Use .contains() instead of strict equality for loose matching
            if (declaringType != null && !declaringType.getQualifiedName().contains("TargetClassName") && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // TODO: Implement Transformation Logic here
            // Example transformation (Rename):
            // invocation.getExecutable().setSimpleName("newMethodName");
            
            System.out.println("Refactored usage at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed");

        // ========================================================================
        // CRITICAL IMPLEMENTATION RULE 1: Preserve Source Code (Sniper Mode)
        // ========================================================================
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // ========================================================================
        // CRITICAL IMPLEMENTATION RULE 2: NoClasspath Compatibility
        // ========================================================================
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types/libs
        launcher.getEnvironment().setAutoImports(true); 

        launcher.addProcessor(new BreakingChangeProcessor());
        
        try {
            launcher.run();
            System.out.println("Migration completed successfully.");
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}