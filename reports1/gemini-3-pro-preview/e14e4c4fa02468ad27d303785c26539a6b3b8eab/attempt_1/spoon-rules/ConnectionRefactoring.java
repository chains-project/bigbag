package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Refactoring Rule for:
 * - METHOD com.api.Client.connect(String, int) [REMOVED]
 * + METHOD com.api.Client.connect(com.api.ConnectionConfig) [ADDED]
 * 
 * Strategy: Wrap arguments (String, int) into `new ConnectionConfig(String, int)`.
 */
public class ConnectionRefactoring {

    public static class ConnectionProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"connect".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // We are looking for the old signature which had 2 arguments.
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            List<CtExpression<?>> args = candidate.getArguments();
            CtExpression<?> arg0 = args.get(0);
            
            // Check Arg 0: Should be String-like (or unknown).
            CtTypeReference<?> type0 = arg0.getType();
            if (type0 != null && !type0.getQualifiedName().contains("String") && !type0.getQualifiedName().equals("<unknown>")) {
                // If we are sure it is NOT a String (and not unknown), it might be the wrong method.
                // However, if it's the NEW type (ConnectionConfig), we definitely skip.
                if (type0.getQualifiedName().contains("ConnectionConfig")) {
                    return false;
                }
            }

            // 4. Owner Check (Relaxed string matching)
            // Ensure we are calling this on a 'Client' class.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Client") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            List<CtExpression<?>> originalArgs = invocation.getArguments();
            CtExpression<?> urlArg = originalArgs.get(0);
            CtExpression<?> timeoutArg = originalArgs.get(1);

            // Transformation: Create `new ConnectionConfig(url, timeout)`
            CtTypeReference<?> configType = factory.Type().createReference("com.api.ConnectionConfig");

            // Create the constructor call
            // Note: We clone the original arguments to detach them from the current AST parent
            CtConstructorCall<?> newConfigObj = factory.Code().createConstructorCall(
                configType,
                urlArg.clone(),
                timeoutArg.clone()
            );

            // Create the new invocation: client.connect(newConfigObj)
            // We preserve the original target (expression before .connect)
            CtInvocation<?> newInvocation = factory.Code().createInvocation(
                invocation.getTarget(), 
                factory.Method().createReference(
                    invocation.getExecutable().getDeclaringType(),
                    factory.Type().voidPrimitiveType(),
                    "connect",
                    configType
                ),
                newConfigObj
            );

            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
            
            System.out.println("Refactored 'connect' at " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden via args or hardcoded here)
        String inputPath = "/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Robust Sniper Configuration for Spoon 11+
        // =========================================================
        
        // 1. Enable comments to ensure they are parsed
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject the SniperJavaPrettyPrinter.
        // This ensures precise preservation of indentation and existing code structure.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive NoClasspath mode (User likely lacks full dependency jars)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ConnectionProcessor());

        System.out.println("Starting Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}