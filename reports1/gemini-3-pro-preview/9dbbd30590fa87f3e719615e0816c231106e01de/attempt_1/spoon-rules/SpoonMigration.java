package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool generated based on dependency diff.
 * 
 * NOTE: The provided diff was empty. This is a TEMPLATE strictly adhering to 
 * the required Spoon configuration (Sniper printer, NoClasspath, Generics safety).
 * Please populate the 'isToBeProcessed' and 'process' methods with logic specific 
 * to your API changes.
 */
public class SpoonMigration {

    public static class GenericRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // TODO: Replace "oldMethodName" with the method name from your diff
            if (!"oldMethodName".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Optional but recommended)
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Owner Check (Defensive coding for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // Use contains() instead of equals() to handle partial resolution
            // TODO: Replace "TargetClassName" with the class owning the method
            if (owner != null && 
                !owner.getQualifiedName().contains("TargetClassName") && 
                !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // TODO: Implement transformation logic here.
            // Example: Rename the method
            // invocation.getExecutable().setSimpleName("newMethodName");

            System.out.println("Refactoring candidate found at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9dbbd30590fa87f3e719615e0816c231106e01de/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9dbbd30590fa87f3e719615e0816c231106e01de/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9dbbd30590fa87f3e719615e0816c231106e01de/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9dbbd30590fa87f3e719615e0816c231106e01de/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES (Preserve Source Code)
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Required for Spoon 11+ fidelity)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Compatibility
        launcher.getEnvironment().setNoClasspath(true);

        // Register Processor
        launcher.addProcessor(new GenericRefactoringProcessor());

        try { 
            launcher.run(); 
            System.out.println("Migration finished. Output: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}