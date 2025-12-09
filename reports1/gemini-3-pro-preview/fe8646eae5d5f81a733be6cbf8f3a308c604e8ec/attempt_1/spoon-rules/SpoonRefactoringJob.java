package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonRefactoringJob {

    /**
     * Processor Implementation
     * Defines the logic for identifying and refactoring code.
     */
    public static class RefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        // CONFIGURATION: Set the target method name and owner here
        private static final String TARGET_METHOD_NAME = "targetMethodName";
        private static final String TARGET_OWNER_CLASS = "TargetClassName";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Scope Check (Defensive String Matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null 
                && !declaringType.getQualifiedName().contains(TARGET_OWNER_CLASS) 
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Argument Check (Example: Ensure it has arguments before processing)
            if (candidate.getArguments().isEmpty()) {
                return false;
            }

            // 4. Type Check (Defensive Pattern for NoClasspath)
            // NEVER assume types are resolved. Check for null.
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = firstArg.getType();

            // Example: If we are migrating FROM primitive int TO something else.
            // If type is known and NOT primitive, it might already be migrated -> Skip.
            if (argType != null && !argType.isPrimitive()) {
                // Check if it's already the new type (e.g., "Duration") to avoid double-processing
                if (argType.getQualifiedName().contains("NewType")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // TODO: Implement your AST transformation logic here.
            // Example: Rename the method
            // invocation.getExecutable().setSimpleName("newMethodName");

            // Example: Wrap an argument (e.g., int -> Duration)
            /*
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            CtTypeReference<?> newTypeRef = factory.Type().createReference("java.time.Duration");
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(newTypeRef),
                factory.Method().createReference(newTypeRef, factory.Type().voidPrimitiveType(), "ofMillis", factory.Type().integerPrimitiveType()),
                originalArg.clone()
            );
            originalArg.replace(replacement);
            */

            System.out.println("Refactored usage at: " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/TokenProviderService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe8646eae5d5f81a733be6cbf8f3a308c604e8ec/attempt_1/transformed");

        // =========================================================
        // CRITICAL: SNIPER MODE & NO-CLASSPATH CONFIGURATION
        // =========================================================
        
        // 1. Enable comments to ensure they aren't stripped
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // =========================================================

        launcher.addProcessor(new RefactoringProcessor());

        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}