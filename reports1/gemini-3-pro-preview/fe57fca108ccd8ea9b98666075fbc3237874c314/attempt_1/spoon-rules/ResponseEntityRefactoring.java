package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Strategy:
 * The diff indicates that `org.springframework.http.ResponseEntity` is source/binary incompatible,
 * and `HttpStatus` has been modified (likely related to the Spring 6 / Spring Boot 3 migration).
 * 
 * In this migration, `ResponseEntity.getStatusCode()` changed its return type from the enum 
 * `HttpStatus` to the interface `HttpStatusCode`.
 * 
 * Existing code like:
 *    HttpStatus status = response.getStatusCode();
 * will fail to compile.
 * 
 * Strategy:
 * Detect invocations of `getStatusCode()` on `ResponseEntity` objects where the result is 
 * assigned to a variable explicitly typed as `HttpStatus`. We inject an explicit cast 
 * to `(HttpStatus)` to preserve source compatibility (assuming the underlying runtime object 
 * is still the Enum).
 */
public class ResponseEntityRefactoring {

    public static class StatusCodeReturnProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"getStatusCode".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Target Type Check (Defensive for NoClasspath)
            // Ensure the method is called on something resembling a ResponseEntity
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                if (targetType != null && !targetType.getQualifiedName().contains("ResponseEntity")) {
                    // It has a type, but it's not ResponseEntity. Skip.
                    return false;
                }
                // If targetType is null, we assume it *might* be ResponseEntity and proceed to check usage context.
            }

            // 3. Check Parent/Context to see if a specific type (HttpStatus) is expected.
            // We only cast if the code expects the old type (HttpStatus).
            CtElement parent = candidate.getParent();
            
            // Case A: Assigned to a local variable
            if (parent instanceof CtLocalVariable) {
                CtLocalVariable<?> variable = (CtLocalVariable<?>) parent;
                return isHttpStatus(variable.getType());
            }

            // Case B: Assigned to an existing variable
            if (parent instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
                // Ensure the invocation is the value being assigned, not the target
                if (assignment.getAssignment() == candidate) {
                    return isHttpStatus(assignment.getAssigned().getType());
                }
            }
            
            // Case C: Return statement
            if (parent instanceof CtReturn) {
                CtMethod<?> method = candidate.getParent(CtMethod.class);
                if (method != null) {
                    return isHttpStatus(method.getType());
                }
            }

            return false;
        }

        private boolean isHttpStatus(CtTypeReference<?> typeRef) {
            if (typeRef == null) return false;
            // Check for explicit "HttpStatus" usage
            return typeRef.getQualifiedName().contains("HttpStatus") 
                && !typeRef.getQualifiedName().contains("HttpStatusCode");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Check if it's already casted (idempotency)
            if (invocation.getParent() instanceof CtTypeCast) {
                CtTypeCast<?> parentCast = (CtTypeCast<?>) invocation.getParent();
                if (isHttpStatus(parentCast.getCastType())) {
                    return;
                }
            }

            // Create the type reference for the cast
            CtTypeReference<?> httpStatusRef = factory.Type().createReference("org.springframework.http.HttpStatus");

            // Create the cast expression: (HttpStatus) response.getStatusCode()
            CtTypeCast<?> cast = factory.Code().createTypeCast(httpStatusRef, invocation.clone());

            // Replace the original invocation with the casted version
            invocation.replace(cast);
            
            System.out.println("Refactored: Injected (HttpStatus) cast at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe57fca108ccd8ea9b98666075fbc3237874c314/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe57fca108ccd8ea9b98666075fbc3237874c314/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve existing documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath mode (Defensive coding assumptions applied in Processor)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StatusCodeReturnProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}