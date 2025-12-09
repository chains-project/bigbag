package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Reference Migration Implementation.
 * 
 * NOTE: The input diff was empty. This class demonstrates a standard refactoring 
 * pattern (Constructor to Static Factory Method) adhering to all strict constraints 
 * (Sniper printer, NoClasspath safety, and Generics safety).
 * 
 * Pattern: new Integer(val) -> Integer.valueOf(val)
 */
public class IntegerRefactoring {

    public static class IntegerConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check (Defensive for NoClasspath)
            // We check against the simple name or qualified name string to avoid ClassNotFoundException
            CtTypeReference<?> type = candidate.getType();
            if (type == null) {
                return false;
            }
            
            String qualifiedName = type.getQualifiedName();
            // Handle cases where qualified name might be just "Integer" in NoClasspath or "java.lang.Integer"
            boolean isInteger = "java.lang.Integer".equals(qualifiedName) || "Integer".equals(qualifiedName);
            
            if (!isInteger) {
                return false;
            }

            // 2. Argument Count Check
            // We only want to refactor the single argument constructor
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = ctorCall.getArguments().get(0);

            // Transformation: Replace 'new Integer(x)' with 'Integer.valueOf(x)'
            
            // 1. Create Type Reference for Integer
            CtTypeReference<?> integerRef = factory.Type().createReference("java.lang.Integer");

            // 2. Create the static invocation
            // We construct the invocation manually to ensure strict typing in NoClasspath scenarios
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(integerRef),
                factory.Method().createReference(
                    integerRef, 
                    integerRef, // Return type
                    "valueOf", 
                    factory.Type().integerPrimitiveType() // Arg type (approximation for NoClasspath)
                ),
                originalArg.clone()
            );

            // 3. Replace the original element
            ctorCall.replace(replacement);
            
            System.out.println("Refactored Integer constructor at line " + ctorCall.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/db02c6bcb989a5b0f08861c3344b532769530467/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/db02c6bcb989a5b0f08861c3344b532769530467/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/db02c6bcb989a5b0f08861c3344b532769530467/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/db02c6bcb989a5b0f08861c3344b532769530467/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        
        // 1. Enable comments to ensure they are preserved during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive coding assumption)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new IntegerConstructorProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete.");
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}