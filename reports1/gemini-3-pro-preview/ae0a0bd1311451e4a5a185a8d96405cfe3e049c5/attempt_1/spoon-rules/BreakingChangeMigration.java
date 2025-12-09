package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool generated for provided Diff.
 * 
 * NOTE: The input diff tag was empty. This class generates a template 
 * adhering to strict Sniper/NoClasspath rules with a placeholder transformation 
 * (renaming 'oldMethod' to 'newMethod').
 */
public class BreakingChangeMigration {

    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Placeholder: 'oldMethod')
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Defensive)
            // Example constraint: only process if it has specific args
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Type Check (Defensive for NoClasspath)
            // NEVER assume types are resolvable. Check for null.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // Check Owner Class (Relaxed string matching for NoClasspath)
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains("TargetClassName") && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                // If we are sure the type is known and it is NOT our target, skip.
                // If it is unknown, we process it to be safe (or strictly skip depending on risk tolerance).
                return false; 
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // LOGIC: Placeholder transformation (Rename method)
            // In a real scenario based on a Diff, this would involve 
            // modifying arguments, wrapping types, or changing the invocation target.
            
            String newMethodName = "newMethod";
            
            // Defensive: Only rename if it hasn't been done (though Spoon usually handles this via the model)
            if (!newMethodName.equals(invocation.getExecutable().getSimpleName())) {
                invocation.getExecutable().setSimpleName(newMethodName);
                System.out.println("Refactored method call at line " + invocation.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/ref/ManifestRefTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve context
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MethodRefactoringProcessor());
        
        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}