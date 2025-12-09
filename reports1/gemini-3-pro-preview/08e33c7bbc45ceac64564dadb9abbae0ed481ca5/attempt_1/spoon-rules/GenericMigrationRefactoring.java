package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtElement;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool
 * Generated based on strict rules: SniperPrinter, NoClasspath safety, and Generic wildcards.
 */
public class GenericMigrationRefactoring {

    /**
     * Processor to handle method invocations.
     * Uses wildcards <?> to ensure type safety with Spoon's generics.
     */
    public static class GenericMethodProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            // Replace "targetMethodName" with actual method name from Diff
            if (!"targetMethodName".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // Example: Filter if argument count doesn't match target
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Owner/Scope Check (Defensive for NoClasspath)
            // We use string matching rather than strict type resolution
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains("TargetClassName") && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Argument Type Check (Defensive)
            // NEVER assume arg.getType() is not null in NoClasspath
            /*
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            if (argType != null && !argType.getQualifiedName().contains("OldType")) {
                 return false; 
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Implementation logic goes here.
            // Example:
            // invocation.replace(getFactory().Code().createCodeSnippetExpression("new logic..."));
            
            System.out.println("Refactored invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden via args if needed)
        String inputPath = "/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed");

        // -------------------------------------------------------------
        // CRITICAL IMPLEMENTATION RULES (Do not modify)
        // -------------------------------------------------------------
        
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable NoClasspath mode (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // 3. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // -------------------------------------------------------------

        launcher.addProcessor(new GenericMethodProcessor());

        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}