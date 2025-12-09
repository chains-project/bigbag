package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule Generator
 * 
 * Generated based on the provided dependency diff.
 * NOTE: The input diff was empty. This is a robust template class configured 
 * with SniperPrinter and NoClasspath strategies, ready for logic injection.
 * 
 * It implements a placeholder refactoring:
 * Renaming 'deprecatedMethod' to 'newMethod'.
 */
public class BreakingChangeMigration {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Placeholder: Adjust based on actual Diff)
            CtExecutableReference<?> executable = candidate.getExecutable();
            if (!"deprecatedMethod".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Example)
            // if (candidate.getArguments().size() != 0) return false;

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // We use .contains() to avoid strict FQN matching which fails without classpath
            CtTypeReference<?> owner = executable.getDeclaringType();
            if (owner != null && 
                !owner.getQualifiedName().equals("<unknown>") && 
                !owner.getQualifiedName().contains("TargetClassName")) {
                return false;
            }

            // 4. Argument Type Check (Defensive pattern)
            // Example: Check if first arg is NOT already the new type
            if (!candidate.getArguments().isEmpty()) {
                CtExpression<?> firstArg = candidate.getArguments().get(0);
                CtTypeReference<?> argType = firstArg.getType();
                
                // If type is known and already correct, skip
                if (argType != null && argType.getQualifiedName().contains("NewType")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // LOGIC: Placeholder transformation (Rename method)
            // Adjust this block based on actual Breaking Change logic (e.g., adding args, wrapping types)
            
            String oldName = invocation.getExecutable().getSimpleName();
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored " + oldName + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/5fca04bd287baf1534baff8cc23cc3dc26dc680d/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5fca04bd287baf1534baff8cc23cc3dc26dc680d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5fca04bd287baf1534baff8cc23cc3dc26dc680d/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5fca04bd287baf1534baff8cc23cc3dc26dc680d/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Preserve Source Code: Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Preserve Source Code: Force SniperJavaPrettyPrinter
        // This ensures precise modification without reformatting unrelated code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding: Enable NoClasspath mode
        // Prevents failures when dependencies are missing
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        launcher.addProcessor(new MigrationProcessor());
        
        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}