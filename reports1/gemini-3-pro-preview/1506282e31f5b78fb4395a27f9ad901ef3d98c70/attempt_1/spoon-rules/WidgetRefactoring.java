package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.ArrayList;
import java.util.List;

/**
 * SPOON REFACTORING SCRIPT
 * 
 * NOTE: The input diff was empty. This script demonstrates the required 
 * robust configuration (Sniper Printer, NoClasspath) using a hypothetical 
 * breaking change scenario:
 * 
 * HYPOTHETICAL CHANGE:
 * - METHOD com.library.Widget.render(String label, int x, int y) [REMOVED]
 * + METHOD com.library.Widget.render(String label, java.awt.Point position) [ADDED]
 */
public class WidgetRefactoring {

    public static class WidgetProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"render".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Old signature has 3 arguments)
            if (candidate.getArguments().size() != 3) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            List<CtExpression<?>> args = candidate.getArguments();
            
            // Check Arg 0: String (or unknown)
            CtTypeReference<?> type0 = args.get(0).getType();
            if (type0 != null && !type0.getQualifiedName().contains("String") && !type0.getQualifiedName().equals("<unknown>")) {
                 return false; 
            }
            
            // Check Arg 1: int (or unknown). If it's already "Point", skip it.
            CtTypeReference<?> type1 = args.get(1).getType();
            if (type1 != null) {
                if (type1.getQualifiedName().contains("Point")) return false; // Already migrated
                // If known type and not int/Integer, skip
                if (!type1.getQualifiedName().equals("int") && !type1.getQualifiedName().equals("java.lang.Integer") && !type1.getQualifiedName().equals("<unknown>")) {
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
            List<CtExpression<?>> args = invocation.getArguments();
            
            // Capture original arguments
            CtExpression<?> labelArg = args.get(0);
            CtExpression<?> xArg = args.get(1);
            CtExpression<?> yArg = args.get(2);

            // Transformation: Wrap x, y into new java.awt.Point(x, y)
            CtTypeReference<?> pointRef = factory.Type().createReference("java.awt.Point");
            
            CtInvocation<?> newPoint = factory.Code().createInvocation(
                null, // Constructor call has no target
                factory.Method().createReference(pointRef, pointRef, "<init>", factory.Type().integerPrimitiveType(), factory.Type().integerPrimitiveType()),
                xArg.clone(),
                yArg.clone()
            );
            // Explicitly set it as a constructor call
            newPoint.setExecutable(factory.createConstructorReference(pointRef));

            // Construct new argument list: [label, newPoint]
            List<CtExpression<?>> newArgs = new ArrayList<>();
            newArgs.add(labelArg.clone());
            newArgs.add(newPoint);
            
            // Apply change
            invocation.setArguments(newArgs);
            
            System.out.println("Refactored Widget.render at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1506282e31f5b78fb4395a27f9ad901ef3d98c70/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/orbiter/OrbiterTokenManagerService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1506282e31f5b78fb4395a27f9ad901ef3d98c70/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1506282e31f5b78fb4395a27f9ad901ef3d98c70/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/orbiter/OrbiterTokenManagerService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1506282e31f5b78fb4395a27f9ad901ef3d98c70/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Preserves indentation/formatting)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);

        // -------------------------------------

        launcher.addProcessor(new WidgetProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Source generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}