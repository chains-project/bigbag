package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Refactoring Rule for:
 * - METHOD com.mylib.Widget.setColor(int, int, int) [REMOVED]
 * + METHOD com.mylib.Widget.setColor(com.mylib.Color) [ADDED]
 */
public class WidgetColorRefactoring {

    public static class SetColorProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setColor".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Old signature took 3 ints)
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 3) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // If we can resolve types, ensure they are primitives/numbers.
            // If types are null (NoClasspath), we proceed cautiously assuming it's the target.
            for (CtExpression<?> arg : args) {
                CtTypeReference<?> type = arg.getType();
                if (type != null && !type.isPrimitive() && !type.getQualifiedName().equals("int")) {
                    // If we clearly see a non-primitive type that isn't int, skip (might be unrelated method)
                    return false;
                }
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Widget") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Capture original arguments (r, g, b)
            List<CtExpression<?>> originalArgs = new ArrayList<>(invocation.getArguments());

            // Define the new type: com.mylib.Color
            CtTypeReference<?> colorTypeRef = factory.Type().createReference("com.mylib.Color");

            // Create Constructor Call: new Color(r, g, b)
            // We clone original args to detach them from the current AST parent safely
            CtConstructorCall<?> newColorInstance = factory.Code().createConstructorCall(
                colorTypeRef,
                originalArgs.get(0).clone(),
                originalArgs.get(1).clone(),
                originalArgs.get(2).clone()
            );

            // Create Replacement Invocation: widget.setColor(new Color(...))
            CtInvocation<?> replacement = factory.Code().createInvocation(
                invocation.getTarget(), // Keep the original target (e.g., 'myWidget')
                invocation.getExecutable(), // Keep the method reference (name matches, sig will update)
                newColorInstance // The single new argument
            );

            // Replace the old invocation
            invocation.replace(replacement);
            
            System.out.println("Refactored Widget.setColor at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden via args)
        String inputPath = "/home/kth/Documents/last_transformer/output/58d2448fa2d6ec02f428b85eaeef0855508e72b9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/58d2448fa2d6ec02f428b85eaeef0855508e72b9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/58d2448fa2d6ec02f428b85eaeef0855508e72b9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/dispatcher/MessageDispatcherProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/58d2448fa2d6ec02f428b85eaeef0855508e72b9/attempt_1/transformed");

        // --- CRITICAL SNIPER CONFIGURATION ---
        // 1. Enable comments to preserve license headers/javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for precise source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode: Do not fail if dependencies are missing
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SetColorProcessor());

        System.out.println("Starting Refactoring on: " + inputPath);
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}