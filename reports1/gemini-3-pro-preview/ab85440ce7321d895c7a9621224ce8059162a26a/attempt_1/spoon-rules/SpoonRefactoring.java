package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

/**
 * Spoon Refactoring Script.
 * 
 * NOTE: The provided dependency diff was empty. 
 * This class contains a TEMPLATE implementation for a generic method rename refactoring
 * (renaming 'deprecatedMethod' to 'newMethod') to demonstrate the required 
 * configuration (Sniper Printer, NoClasspath, Defensive Coding).
 */
public class SpoonRefactoring {

    public static class RefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            // Replace "deprecatedMethod" with the actual method name from the diff
            if (!"deprecatedMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Scope Check (Defensive for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // Check if the owner is known and matches the target class (e.g., "com.example.OldClass")
            // Using .contains() accounts for simple names vs qualified names in NoClasspath
            if (owner != null && !owner.getQualifiedName().contains("OldClass") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Argument Count Check
            // Example: Assuming the method has 1 argument
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 4. Type Check (Defensive)
            // NEVER assume types are resolved in NoClasspath.
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Example: If we only want to refactor String arguments
            // If type is null (unknown), we assume it matches to be safe (or check variable names).
            // If type is known, we verify it.
            if (type != null && !type.getQualifiedName().contains("String")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Logic: Rename the method
            // In a real scenario, this might involve wrapping arguments, changing types, etc.
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed");

        // ==========================================================
        // CRITICAL IMPLEMENTATION RULES (Sniper & NoClasspath)
        // ==========================================================
        
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve indentation/formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // ==========================================================

        launcher.addProcessor(new RefactoringProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}