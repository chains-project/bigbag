package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ResponseEntityRefactoring {

    public static class ResponseEntityProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final Set<String> ENUM_METHODS = new HashSet<>(Arrays.asList(
            "name", "ordinal", "series", "getReasonPhrase"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            String methodName = candidate.getExecutable().getSimpleName();
            
            // We are interested in getRawStatusCode (Removed) and getStatusCode (Changed Return Type)
            if (!"getRawStatusCode".equals(methodName) && !"getStatusCode".equals(methodName)) {
                return false;
            }

            // Defensive Check: Owner Type
            // In NoClasspath, declaring type might be inferred or null, so we check loose string matching
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String qualName = declaringType.getQualifiedName();
                // Check if it's ResponseEntity (or a subclass/unknown in loose mode)
                return qualName.contains("ResponseEntity") || qualName.equals("<unknown>");
            }
            
            // Fallback: Check the target expression type if available
            CtExpression<?> target = candidate.getTarget();
            if (target != null && target.getType() != null) {
                return target.getType().getQualifiedName().contains("ResponseEntity");
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();

            if ("getRawStatusCode".equals(methodName)) {
                refactorGetRawStatusCode(invocation);
            } else if ("getStatusCode".equals(methodName)) {
                refactorGetStatusCode(invocation);
            }
        }

        /**
         * Rule: response.getRawStatusCode() -> response.getStatusCode().value()
         */
        private void refactorGetRawStatusCode(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> target = invocation.getTarget(); // The 'response' object

            // 1. Create invocation: target.getStatusCode()
            CtInvocation<?> getStatusCodeCall = factory.Code().createInvocation(
                target != null ? target.clone() : null,
                factory.Method().createReference(
                    factory.Type().createReference("org.springframework.http.ResponseEntity"),
                    factory.Type().createReference("org.springframework.http.HttpStatusCode"),
                    "getStatusCode"
                )
            );

            // 2. Create invocation: .value()
            CtInvocation<?> getValueCall = factory.Code().createInvocation(
                getStatusCodeCall,
                factory.Method().createReference(
                    factory.Type().createReference("org.springframework.http.HttpStatusCode"),
                    factory.Type().integerPrimitiveType(),
                    "value"
                )
            );

            // 3. Replace original
            invocation.replace(getValueCall);
            System.out.println("Refactored: getRawStatusCode() -> getStatusCode().value() at line " + invocation.getPosition().getLine());
        }

        /**
         * Rule: response.getStatusCode() -> ((HttpStatus) response.getStatusCode())
         * Applied IF the return value is used in a context requiring the old Enum type (assignment or specific method calls).
         */
        private void refactorGetStatusCode(CtInvocation<?> invocation) {
            CtElement parent = invocation.getParent();
            boolean needsCast = false;

            // Check 1: Is it assigned to a variable explicitly typed as 'HttpStatus'?
            if (parent instanceof CtLocalVariable) {
                CtLocalVariable<?> var = (CtLocalVariable<?>) parent;
                if (isHttpStatusType(var.getType())) {
                    needsCast = true;
                }
            } else if (parent instanceof CtAssignment) {
                CtAssignment<?,?> assignment = (CtAssignment<?,?>) parent;
                if (isHttpStatusType(assignment.getAssigned().getType())) {
                    needsCast = true;
                }
            }

            // Check 2: Is a method called on the result that implies it must be HttpStatus?
            // e.g., getStatusCode().series() or getStatusCode().name()
            if (parent instanceof CtInvocation) {
                CtInvocation<?> parentInv = (CtInvocation<?>) parent;
                // Ensure our invocation is the TARGET (receiver) of the parent call
                if (parentInv.getTarget() == invocation) {
                    String parentMethodName = parentInv.getExecutable().getSimpleName();
                    if (ENUM_METHODS.contains(parentMethodName)) {
                        needsCast = true;
                    }
                }
            }

            if (needsCast) {
                Factory factory = getFactory();
                CtTypeReference<?> httpStatusRef = factory.Type().createReference("org.springframework.http.HttpStatus");
                
                // Create Cast Expression: (HttpStatus) invocation
                CtExpression<?> castedExpression = factory.Code().createTypeCast(httpStatusRef, invocation.clone());
                
                invocation.replace(castedExpression);
                System.out.println("Refactored: Injected (HttpStatus) cast for getStatusCode() at line " + invocation.getPosition().getLine());
            }
        }

        private boolean isHttpStatusType(CtTypeReference<?> typeRef) {
            return typeRef != null 
                && typeRef.getQualifiedName().contains("HttpStatus") 
                && !typeRef.getQualifiedName().contains("HttpStatusCode");
        }
    }

    public static void main(String[] args) {
        // User configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4b9fdf1135b704e050cf785d6ea353d24f4c64bb/attempt_1/transformed");

        // 1. Enable comments to preserve license headers and javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // 3. Force Sniper Printer for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        launcher.addProcessor(new ResponseEntityProcessor());

        try {
            System.out.println("Starting Refactoring: Spring ResponseEntity Migration...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}