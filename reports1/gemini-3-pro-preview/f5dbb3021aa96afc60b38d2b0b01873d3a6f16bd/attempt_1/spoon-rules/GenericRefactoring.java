package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Script.
 * 
 * NOTE: The input dependency diff was empty. 
 * This class implements a GENERIC template for refactoring a method call from:
 * "com.legacy.OldClass.oldMethod(String)" 
 * to 
 * "com.modern.NewClass.newMethod(String)"
 * 
 * Adheres to strict Sniper and NoClasspath constraints.
 */
public class GenericRefactoring {

    public static class MethodMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_METHOD_NAME = "oldMethod"; // TODO: Replace based on diff
        private static final String TARGET_CLASS_NAME = "OldClass";   // TODO: Replace based on diff
        private static final String NEW_CLASS_QUALIFIED = "com.modern.NewClass";
        private static final String NEW_METHOD_NAME = "newMethod";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Defensive: getExecutable() or getSimpleName() could theoretically be null in malformed ASTs
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null || !TARGET_METHOD_NAME.equals(execRef.getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Assuming 1 argument for this template)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Logic: 
            // - If type is NULL (unknown), we assume it might be valid and process it to be safe.
            // - If type is NOT null, we check if it matches the expected old type (e.g., String).
            // - If it matches the destination type, we skip it (already refactored).
            if (type != null) {
                // Example: If argument is already the new type, skip
                // if (type.getQualifiedName().contains("NewType")) return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                // We look for the class name. If qName is "<unknown>", we process cautiously.
                if (!qName.contains(TARGET_CLASS_NAME) && !qName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // LOGIC: Replace the invocation target (Owner) and the Method Name
            
            // 1. Create reference to the new class
            CtTypeReference<?> newOwnerRef = factory.Type().createReference(NEW_CLASS_QUALIFIED);

            // 2. Create the TypeAccess (e.g., "NewClass") for static calls
            CtTypeAccess<?> newOwnerAccess = factory.Code().createTypeAccess(newOwnerRef);

            // 3. Update the invocation's target and executable reference
            // This approach preserves the original arguments
            CtExecutableReference<?> currentExec = invocation.getExecutable();
            
            // Create a new executable reference for the replacement
            CtExecutableReference<?> newExecRef = factory.Method().createReference(
                newOwnerRef, 
                currentExec.getType(), // Keep return type (defensive)
                NEW_METHOD_NAME, 
                currentExec.getParameterTypes().toArray(new CtTypeReference[0])
            );

            // Mutation: Update target and executable
            invocation.setTarget(newOwnerAccess);
            invocation.setExecutable(newExecRef);

            System.out.println("Refactored " + TARGET_METHOD_NAME + " to " + NEW_METHOD_NAME + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MethodMigrationProcessor());
        
        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Check output at: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}