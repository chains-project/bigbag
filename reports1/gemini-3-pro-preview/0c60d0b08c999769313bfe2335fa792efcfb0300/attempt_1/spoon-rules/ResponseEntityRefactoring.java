package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ResponseEntityRefactoring {

    public static class StatusCodeValueProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"getStatusCodeValue".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check (getStatusCodeValue takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // We check if the target of the invocation (e.g., "response" in "response.getStatusCodeValue()")
            // seems to be a ResponseEntity.
            CtExpression<?> target = candidate.getTarget();
            
            // If explicit target is missing (e.g. call inside the class), we skip or handle cautiously.
            // Here we assume typical usage 'entity.getStatusCodeValue()'
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                if (targetType != null && !targetType.getQualifiedName().contains("ResponseEntity")) {
                    // It has a known type, and it's NOT ResponseEntity -> Skip
                    return false;
                }
                // If targetType is null (unknown classpath), we assume it's a match based on the unique method name.
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> target = invocation.getTarget(); // e.g., responseEntity

            // Goal: Replace `target.getStatusCodeValue()` 
            // With: `target.getStatusCode().value()`

            // 1. Create reference for `getStatusCode()`
            CtExecutableReference<Object> getStatusCodeRef = factory.Executable().createReference(
                null, // declaring type (inferred or unknown)
                null, // return type (inferred)
                "getStatusCode"
            );

            // 2. Create invocation: target.getStatusCode()
            // We clone target to preserve formatting/comments of the variable access
            CtInvocation<?> getStatusCodeInvocation = factory.Code().createInvocation(
                (target != null) ? target.clone() : null, 
                getStatusCodeRef
            );

            // 3. Create reference for `.value()`
            // Note: In Spring 6, getStatusCode() returns HttpStatusCode interface, which has .value()
            CtExecutableReference<Object> valueRef = factory.Executable().createReference(
                null, 
                factory.Type().integerPrimitiveType(), 
                "value"
            );

            // 4. Create invocation: [target.getStatusCode()].value()
            CtInvocation<?> replacement = factory.Code().createInvocation(
                getStatusCodeInvocation,
                valueRef
            );

            // 5. Apply replacement
            invocation.replace(replacement);
            
            System.out.println("Refactored getStatusCodeValue() at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0c60d0b08c999769313bfe2335fa792efcfb0300/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0c60d0b08c999769313bfe2335fa792efcfb0300/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer for high-fidelity preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath Mode (Robustness)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StatusCodeValueProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}