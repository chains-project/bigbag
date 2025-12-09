package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Script generated for an empty dependency diff.
 * 
 * SCENARIO:
 * Since no specific diff was provided in the <dependency_change_diff> tags,
 * this class implements a TEMPLATE transformation for a common scenario:
 * 
 * HYPOTHETICAL CHANGE:
 * - METHOD com.example.Service.oldMethod() [REMOVED]
 * + METHOD com.example.Service.newMethod() [ADDED]
 * 
 * This template strictly adheres to:
 * 1. SniperJavaPrettyPrinter (preserves formatting/comments).
 * 2. NoClasspath mode (defensive type checking).
 */
public class SpoonMigration {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Hypothetical: "oldMethod")
            if (!"oldMethod".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive / NoClasspath safe)
            // We use string matching instead of strict type resolution
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String qualifiedName = owner.getQualifiedName();
                // Check if the owner is the class we care about (e.g., "com.example.Service")
                if (!qualifiedName.contains("Service") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument Check (Example: expecting 0 arguments)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 4. Double Transformation Prevention
            // In NoClasspath, we might not know the return type, but if the method was already
            // renamed to "newMethod", step 1 catches it. 
            // If checking arguments, ensure we don't process already converted types.
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // LOGIC: Rename the method
            // In a real scenario, you might modify arguments or wrap expressions here.
            
            // 1. Create reference to the new method name
            String newMethodName = "newMethod";
            
            // 2. Update the invocation's executable reference
            invocation.getExecutable().setSimpleName(newMethodName);
            
            System.out.println("Refactored 'oldMethod' to 'newMethod' at " + 
                invocation.getPosition().getFile().getName() + ":" + 
                invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // ========================================================================
        // CRITICAL IMPLEMENTATION RULES (Sniper & NoClasspath)
        // ========================================================================
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // ========================================================================

        launcher.addProcessor(new MigrationProcessor());
        
        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}