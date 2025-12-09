package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * 
 * NOTE: The input diff provided was empty. This class demonstrates the 
 * "Critical Implementation Rules" using a placeholder scenario:
 * 
 * Scenario:
 * - OLD: com.legacy.OldClass.oldMethod(String)
 * - NEW: com.modern.NewClass.newMethod(String)
 */
public class BreakingChangeMigration {

    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Target the old method name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"oldMethod".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // We expect the argument to be a String. 
            // If Spoon can't resolve the type (null), we assume it might be valid and proceed.
            // If Spoon resolves it and it's definitely NOT a String, we skip.
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            if (argType != null && !argType.getQualifiedName().equals("java.lang.String") && !argType.getQualifiedName().equals("String")) {
                // In NoClasspath, simple names might occur
                return false; 
            }

            // 4. Owner Check (Relaxed string matching)
            // Check if the method belongs to "OldClass"
            CtExecutableReference<?> executable = candidate.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            
            if (declaringType != null) {
                String ownerName = declaringType.getQualifiedName();
                // If the owner is known and does not match our target, skip.
                // We allow "<unknown>" or null owners to pass in NoClasspath mode if the method name matches.
                if (!ownerName.contains("OldClass") && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation Logic: 
            // Change `OldClass.oldMethod(arg)` -> `NewClass.newMethod(arg)`
            
            // 1. Define the new owning class
            CtTypeReference<?> newOwnerRef = factory.Type().createReference("com.modern.NewClass");

            // 2. Create the invocation
            // We preserve the original argument (cloning it to detach from old tree)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(newOwnerRef),
                factory.Method().createReference(newOwnerRef, factory.Type().voidPrimitiveType(), "newMethod", factory.Type().stringType()),
                originalArg.clone()
            );

            // 3. Apply replacement
            invocation.replace(replacement);
            
            System.out.println("Refactored 'oldMethod' at " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe31c5e11259881e9dce66d325d1b8b8ed8afc81/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: SNIPER PRINTER CONFIGURATION
        // Ensures comments and formatting are preserved exactly.
        // ==========================================================
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // ==========================================================
        // CRITICAL: NO-CLASSPATH CONFIGURATION
        // Ensures the processor runs without full library JARs.
        // ==========================================================
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // Add the processor
        launcher.addProcessor(new MethodRefactoringProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Check output at: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}