package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Strategy deduced from (hypothetical) diff:
 * - METHOD com.payment.Gateway.charge(double) [REMOVED]
 * + METHOD com.payment.Gateway.charge(java.math.BigDecimal) [ADDED]
 *
 * Transformation:
 * Wraps double/float arguments in BigDecimal.valueOf(...) to match the new API signature.
 */
public class PaymentGatewayRefactoring {

    public static class GatewayProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"charge".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Rule: If type is known and is already BigDecimal, skip it.
            if (type != null && type.getQualifiedName().contains("BigDecimal")) {
                return false;
            }
            
            // Rule: If type is null (unknown) OR it is a primitive (double/float), we process it.
            // Note: In NoClasspath, type might be null, so we assume it needs migration if it's not explicitly the new type.
            
            // 4. Owner Check (Relaxed string matching for NoClasspath safety)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null 
                && !owner.getQualifiedName().contains("Gateway") 
                && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation: Wrap originalArg inside BigDecimal.valueOf(...)
            CtTypeReference<?> bigDecimalRef = factory.Type().createReference("java.math.BigDecimal");

            // We use 'valueOf' instead of constructor for better handling of doubles/floats
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(bigDecimalRef),
                factory.Method().createReference(
                    bigDecimalRef, 
                    bigDecimalRef, // Return type
                    "valueOf", 
                    factory.Type().doublePrimitiveType() // Arg type hint
                ),
                originalArg.clone()
            );

            // Replace the argument, not the whole invocation, to preserve the outer method call structure
            originalArg.replace(replacement);
            
            System.out.println("Refactored 'charge' method at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/CachingProxyITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/CachingProxyITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES (Sniper Mode)
        // 1. Enable comments to preserve license headers and Javadoc
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to ensure strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Defensive Coding)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new GatewayProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}