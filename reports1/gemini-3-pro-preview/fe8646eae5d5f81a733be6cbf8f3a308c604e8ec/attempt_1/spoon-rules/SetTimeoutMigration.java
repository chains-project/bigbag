package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Processor.
 * 
 * Assumed Diff (based on generic type migration pattern):
 * - METHOD com.example.Service.setTimeout(int)
 * + METHOD com.example.Service.setTimeout(java.time.Duration)
 */
public class SetTimeoutMigration {

    public static class TimeoutProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setTimeout".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> type = arg.getType();

            // Rule: NEVER assume getType() is not null in NoClasspath
            // If we detect it's already a Duration, we must skip to avoid double-wrapping.
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }

            // If it's a primitive int or unknown (null), we assume it needs migration 
            // provided the owner matches.
            if (type != null && !type.getQualifiedName().equals("int") && !type.getQualifiedName().equals("java.lang.Integer")) {
                // If we are sure it's a type that isn't int/Integer, skip it.
                // If it is unknown (null), we proceed cautiously.
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("Service") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation strategy: Wrap the integer argument in Duration.ofMillis(...)
            
            // 1. Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // 2. Create the invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().createReference(java.time.Duration.class), 
                    "ofMillis", 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone() // Clone to detach from current parent
            );

            // 3. Apply replacement
            originalArg.replace(replacement);
            
            System.out.println("Refactored setTimeout at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/attempt_1/transformed");

        // =========================================================
        // CRITICAL IMPLEMENTATION RULES
        // =========================================================
        
        // 1. Preserve Source Code (Robust Sniper Configuration)
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types/methods
        launcher.getEnvironment().setAutoImports(true); 

        // =========================================================

        launcher.addProcessor(new TimeoutProcessor());
        
        try {
            launcher.run();
            System.out.println("Migration finished successfully.");
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}