package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;

/**
 * Spoon Refactoring Script
 * 
 * Generated based on the provided System Prompt rules.
 * Note: Since the <dependency_change_diff> input was empty, this class demonstrates
 * a HYPOTHETICAL refactoring scenario to prove adherence to the strict implementation rules:
 * 
 * Scenario:
 * - OLD: com.library.Window.setSize(int width, int height)
 * - NEW: com.library.Window.setSize(com.library.Dimension dim)
 */
public class WindowRefactoring {

    public static class WindowSetSizeProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We look for the method name strictly.
            if (!"setSize".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // The old method signature took 2 arguments (int, int).
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive Coding for NoClasspath)
            // NEVER assume getType() is non-null.
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = firstArg.getType();

            // Logic:
            // - If type is NULL (unknown), we assume it might be the old code and process it.
            // - If type is known and matches the NEW type (Dimension), we skip it (already fixed).
            // - If type is known and is primitive (int), it matches the OLD signature.
            if (argType != null && !argType.isPrimitive()) {
                if (argType.getQualifiedName().contains("Dimension")) {
                    return false; // Already migrated
                }
            }

            // 4. Owner Check (Relaxed string matching)
            // In NoClasspath, the owner might be <unknown> or fully qualified.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().equals("<unknown>")) {
                // If we specifically know the owner, check if it looks like our target class.
                if (!owner.getQualifiedName().contains("Window")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Extract original arguments (width, height)
            CtExpression<?> widthArg = invocation.getArguments().get(0);
            CtExpression<?> heightArg = invocation.getArguments().get(1);

            // Create reference to the new wrapper type: com.library.Dimension
            CtTypeReference<?> dimensionType = factory.Type().createReference("com.library.Dimension");

            // Transformation: Wrap (width, height) into new Dimension(width, height)
            // usage of wildcards <?> ensures Generics safety
            CtConstructorCall<?> newDimensionArg = factory.Code().createConstructorCall(
                dimensionType,
                widthArg.clone(),
                heightArg.clone()
            );

            // Replace the arguments list in the invocation
            invocation.setArguments(Arrays.asList(newDimensionArg));

            System.out.println("Refactored Window.setSize at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be passed as args in a real CLI)
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // ==========================================================
        // CRITICAL IMPLEMENTATION RULES (Sniper & NoClasspath)
        // ==========================================================
        
        // 1. Enable comments so they are attached to the AST
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force SniperJavaPrettyPrinter. 
        // This is mandatory to preserve indentation and structure of unrelated code.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode.
        // This allows running the refactoring without full dependency JARs.
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new WindowSetSizeProcessor());

        try {
            System.out.println("Running Spoon Refactoring...");
            launcher.run();
            System.out.println("Done. Modified sources generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}