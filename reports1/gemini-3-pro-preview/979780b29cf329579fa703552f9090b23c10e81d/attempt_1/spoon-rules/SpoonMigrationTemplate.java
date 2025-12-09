package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonMigrationTemplate {

    /**
     * Processor to handle breaking changes.
     * HYPOTHETICAL SCENARIO: 
     * Refactor `service.setTimeout(int ms)` -> `service.configureTimeout(Duration.ofMillis(ms))`
     */
    public static class BreakingChangeProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_METHOD_NAME = "setTimeout";
        private static final String NEW_METHOD_NAME = "configureTimeout";
        private static final String TARGET_CLASS_TOKEN = "Service"; // Relaxed match for NoClasspath

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Scope Check (Defensive)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains(TARGET_CLASS_TOKEN) && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // If we know the type is already the target (Duration), skip it to avoid double-processing
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }
            
            // If type is known and is NOT int/Integer, we might want to skip (context dependent)
            // For NoClasspath, if type is null, we usually process it assuming the name match is sufficient
            if (type != null && !type.getQualifiedName().contains("int") && !type.getQualifiedName().contains("Integer")) {
                // return false; // Uncomment if you want strict primitive checking
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // 1. Rename the method
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);

            // 2. Create the argument wrapper: Duration.ofMillis(originalArg)
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");
            
            CtInvocation<?> wrappedArg = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    durationRef, // return type
                    "ofMillis", 
                    factory.Type().longPrimitiveType() // param type
                ),
                originalArg.clone() // Clone the original expression to move it
            );

            // 3. Replace the argument
            originalArg.replace(wrappedArg);
            
            System.out.println("Refactored " + TARGET_METHOD_NAME + " at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable via args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/handler/request/RequestMessageHandlerService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/handler/request/RequestMessageHandlerService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: SNIPER MODE CONFIGURATION
        // ==========================================================
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter manually for precise reproduction
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode (Robustness against missing libs)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new BreakingChangeProcessor());

        try {
            System.out.println("Starting refactoring on: " + inputPath);
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}