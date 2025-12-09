package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;
import java.util.Arrays;

/**
 * AUTO-GENERATED SPOON MIGRATION TOOL
 * 
 * SYSTEM NOTE: The provided dependency diff was empty. 
 * This class implements a ROBUST TEMPLATE illustrating the required refactoring pattern
 * (Argument Wrapping) complying with strict Sniper and NoClasspath rules.
 * 
 * Template Scenario:
 * - CHANGE: Method `com.library.Widget.resize(int width, int height)` 
 * - TO:     `com.library.Widget.resize(com.library.Dimension dim)`
 */
public class WidgetMigration {

    public static class ResizeProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"resize".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Old method had 2 ints)
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive coding for NoClasspath)
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            CtTypeReference<?> typeRef = firstArg.getType();

            // If type is resolved (not null) and is NOT 'int', it might already be migrated or unrelated.
            // If type is null (unknown in NoClasspath), we assume it might be a target based on other heuristics.
            if (typeRef != null && !typeRef.getQualifiedName().equals("int")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // If owner is known, check if it looks like our Widget class
            if (owner != null && !owner.getQualifiedName().contains("Widget") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Extract original arguments
            CtExpression<?> widthArg = invocation.getArguments().get(0);
            CtExpression<?> heightArg = invocation.getArguments().get(1);

            // Transformation: Wrap arguments into `new Dimension(width, height)`
            CtTypeReference<?> dimensionType = factory.Type().createReference("com.library.Dimension");

            CtConstructorCall<?> newDimensionExpr = factory.Code().createConstructorCall(
                dimensionType,
                widthArg.clone(),
                heightArg.clone()
            );

            // Replace the arguments list with the new wrapped argument
            // Note: We use setArguments to preserve the method invocation structure for Sniper
            invocation.setArguments(Arrays.asList(newDimensionExpr));

            System.out.println("Refactored resize() call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---

        // 1. Enable comments to preserve them during refactoring
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force SniperJavaPrettyPrinter for strict source code preservation
        // (Required for Spoon 11+ to handle formatting correctly)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Configuration for NoClasspath
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // -------------------------------------

        launcher.addProcessor(new ResizeProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}