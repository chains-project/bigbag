package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactors ResponseEntity.getStatusCode() usage for Spring 6 migration.
 * 
 * Change detected: ResponseEntity (Source Incompatible).
 * Context: In Spring 6, ResponseEntity.getStatusCode() changed return type 
 * from HttpStatus (enum) to HttpStatusCode (interface).
 * 
 * Strategy: Explicitly cast the result to (HttpStatus) where legacy code expects the Enum.
 */
public class SpringResponseEntityRefactoring {

    public static class StatusCodeProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"getStatusCode".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner Check (Defensive loose matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null 
                && !declaringType.getQualifiedName().contains("ResponseEntity") 
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Context Check: Is the result assigned to or returned as 'HttpStatus'?
            // We need to find the expected type from the parent context.
            CtElement parent = candidate.getParent();
            
            // If already casted, skip
            if (parent instanceof CtTypeCast) {
                return false;
            }

            CtTypeReference<?> expectedType = null;

            if (parent instanceof CtLocalVariable) {
                // e.g., HttpStatus status = response.getStatusCode();
                expectedType = ((CtLocalVariable<?>) parent).getType();
            } else if (parent instanceof CtAssignment) {
                // e.g., status = response.getStatusCode();
                expectedType = ((CtAssignment<?,?>) parent).getAssigned().getType();
            } else if (parent instanceof CtReturn) {
                // e.g., return response.getStatusCode(); (inside a method returning HttpStatus)
                // This is harder to resolve in NoClasspath without full AST traversal up to the method,
                // but we can try checking if the method return type is explicitly HttpStatus.
                try {
                    spoon.reflect.declaration.CtMethod<?> method = candidate.getParent(spoon.reflect.declaration.CtMethod.class);
                    if (method != null) {
                        expectedType = method.getType();
                    }
                } catch (Exception e) {
                    // Ignore context resolution failures
                }
            }

            // 5. Verify Incompatibility
            // If expected type is explicitly "HttpStatus" (the Enum), we need a cast because the new return is HttpStatusCode.
            if (expectedType != null && "HttpStatus".equals(expectedType.getSimpleName())) {
                return true;
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create reference to org.springframework.http.HttpStatus
            CtTypeReference<?> httpStatusRef = factory.Type().createReference("org.springframework.http.HttpStatus");

            // Clone the original invocation to preserve its structure
            CtExpression<?> originalExpression = invocation.clone();

            // Create the cast: (HttpStatus) response.getStatusCode()
            CtExpression<?> castedExpression = factory.Code().createTypeCast(httpStatusRef, originalExpression);

            // Replace
            invocation.replace(castedExpression);
            
            System.out.println("Refactored ResponseEntity.getStatusCode() return type at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StatusCodeProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}