package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Script.
 * 
 * Generated based on the breaking change:
 * - METHOD com.library.OldClass.oldMethod() [REMOVED/RENAMED]
 * + METHOD com.library.NewClass.newMethod() [ADDED]
 * 
 * Strategy:
 * 1. Detect invocations of `oldMethod`.
 * 2. Verify the owner is `OldClass` (defensively).
 * 3. Update the method name to `newMethod`.
 * 4. Update the target (owner) class to `NewClass`.
 */
public class LibraryMigration {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            // We look for the old method name.
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Owner Type (Defensive for NoClasspath)
            // We check if the declaring type of the method matches the old class.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If declaringType is null (unresolved) or matches our target, we proceed.
            // Using loose string matching to handle FQCN variations.
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains("OldClass") && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Optional: Check Argument Count (assuming 0 args for this example)
            // if (candidate.getArguments().size() != 0) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Logic
            
            // 1. Rename the method
            invocation.getExecutable().setSimpleName("newMethod");

            // 2. Change the Static Target / Owner (if it's a static call)
            // e.g., OldClass.oldMethod() -> NewClass.newMethod()
            if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                CtTypeReference<?> newOwnerRef = getFactory().Type().createReference("com.library.NewClass");
                
                // If it was a static access (TypeAccess), replace it
                if (invocation.getTarget() instanceof spoon.reflect.code.CtTypeAccess) {
                    invocation.setTarget(getFactory().Code().createTypeAccess(newOwnerRef));
                }
                
                // Update the declaring type of the executable reference to ensure imports are handled correctly
                invocation.getExecutable().setDeclaringType(newOwnerRef);
            }

            System.out.println("Refactored 'oldMethod' to 'newMethod' at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed");

        // ===========================================
        // CRITICAL: Preserve Source Code formatting
        // ===========================================
        
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter for high-fidelity preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // ===========================================
        // CRITICAL: Defensive / NoClasspath Mode
        // ===========================================
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        // Register the processor
        launcher.addProcessor(new MethodRenameProcessor());

        System.out.println("Starting Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}