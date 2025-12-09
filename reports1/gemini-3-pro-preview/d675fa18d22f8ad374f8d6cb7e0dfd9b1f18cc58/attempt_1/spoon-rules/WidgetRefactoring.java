package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;

/**
 * Refactoring Rule for:
 * - METHOD com.library.Widget.resize(int, int) [REMOVED]
 * + METHOD com.library.Widget.resize(com.library.Dimension) [ADDED]
 */
public class WidgetRefactoring {

    public static class WidgetResizeProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"resize".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // We are looking for the old signature (2 ints), not the new one (1 Dimension)
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            CtTypeReference<?> firstArgType = firstArg.getType();

            // If the first argument is already a Dimension (or subtype), the code is arguably already migrated or unrelated.
            // We only process if type is null (unknown) or primitive/integer.
            if (firstArgType != null && !firstArgType.isPrimitive() && firstArgType.getQualifiedName().contains("Dimension")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Widget") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Extract original arguments (width, height)
            CtExpression<?> widthArg = invocation.getArguments().get(0);
            CtExpression<?> heightArg = invocation.getArguments().get(1);

            // Transformation: Wrap width and height inside new Dimension(w, h)
            CtTypeReference<?> dimensionRef = factory.Type().createReference("com.library.Dimension");

            // Create constructor call: new Dimension(width, height)
            // Note: We must clone() arguments because an AST node cannot have two parents.
            CtConstructorCall<?> newDimensionCall = factory.Code().createConstructorCall(
                dimensionRef, 
                widthArg.clone(), 
                heightArg.clone()
            );

            // Replace the arguments list of the invocation
            invocation.setArguments(Arrays.asList(newDimensionCall));

            System.out.println("Refactored Widget.resize at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Source preservation
        
        // 1. Enable comments to ensure they are parsed
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new WidgetResizeProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}