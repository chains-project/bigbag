package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Script generated for Empty/Template Diff.
 * 
 * This script demonstrates the required Sniper configuration and defensive coding
 * strategies for Spoon 11+ in NoClasspath mode.
 * 
 * Scenario implemented (Template):
 * Refactoring a hypothetical method:
 * - OLD: com.example.LegacyService.performTask(int milliseconds)
 * - NEW: com.example.ModernService.execute(java.time.Duration)
 */
public class MigrationScript {

    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"performTask".equals(methodName)) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching because resolving types might fail without full classpath.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && !declaringType.getQualifiedName().contains("LegacyService")) {
                // If the type is known and doesn't match our target, skip it.
                // Note: If declaringType is null/unknown, we might process it to be safe 
                // (false positives are better than false negatives in migration), 
                // or skip depending on strictness requirements.
                return false;
            }

            // 3. Argument Check (Defensive)
            // We expect 1 argument (int).
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // If we know the type is already Duration, we skip (idempotency).
            if (argType != null && argType.getQualifiedName().contains("Duration")) {
                return false;
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // --- Step 1: Handle Argument Transformation (int -> Duration) ---
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            
            // Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");
            
            // Create Duration.ofMillis(originalArg)
            CtInvocation<?> newArg = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    "ofMillis", 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone()
            );
            
            // Replace the argument in the invocation
            originalArg.replace(newArg);

            // --- Step 2: Handle Method Renaming (performTask -> execute) ---
            CtExecutableReference<?> execRef = invocation.getExecutable();
            execRef.setSimpleName("execute");

            // --- Step 3: Handle Owner Change (LegacyService -> ModernService) ---
            // Only necessary if it's a static call: LegacyService.performTask(...)
            if (invocation.getTarget() != null && invocation.getTarget().toString().contains("LegacyService")) {
                CtTypeReference<?> modernTypeRef = factory.Type().createReference("com.example.ModernService");
                invocation.getTarget().replace(factory.Code().createTypeAccess(modernTypeRef));
            }
            
            System.out.println("Refactored method at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Standard Launcher Setup
        Launcher launcher = new Launcher();
        
        // Input/Output Paths
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/aisec/AisecTokenManagerService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed");

        // CRITICAL: NoClasspath Configuration
        // Spoon will not fail if dependencies are missing.
        launcher.getEnvironment().setNoClasspath(true);

        // CRITICAL: Sniper Printer Configuration
        // 1. Enable comments to be parsed and attached to AST nodes.
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject the SniperJavaPrettyPrinter.
        // This ensures that only modified AST nodes are reprinted, preserving
        // original formatting, indentation, and comments in untouched code.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Add the processor
        launcher.addProcessor(new MethodRefactoringProcessor());

        // Run the transformation
        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}