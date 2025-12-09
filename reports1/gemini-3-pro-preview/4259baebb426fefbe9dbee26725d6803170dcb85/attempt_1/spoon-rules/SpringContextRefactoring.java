package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringContextRefactoring {

    /**
     * Processor to enforce explicit casting of RequestContextHolder.getRequestAttributes()
     * to (ServletRequestAttributes). This fixes source compatibility issues where
     * the return type or generic bounds of the API have tightened or changed.
     */
    public static class RequestAttributesCastProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"getRequestAttributes".equals(methodName) && 
                !"currentRequestAttributes".equals(methodName)) {
                return false;
            }

            // 2. Check Owner Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains("RequestContextHolder")) {
                return false;
            }

            // 3. Check if ALREADY Casted (Prevent double casting)
            if (candidate.getParent() instanceof CtUnaryOperator) {
                CtUnaryOperator<?> parentOp = (CtUnaryOperator<?>) candidate.getParent();
                if (parentOp.getKind() == UnaryOperatorKind.CAST) {
                    CtTypeReference<?> castType = parentOp.getType();
                    // If already casting to ServletRequestAttributes (or checking generic wildcard), skip
                    if (castType != null && castType.getQualifiedName().contains("ServletRequestAttributes")) {
                        return false;
                    }
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Define the target type: org.springframework.web.context.request.ServletRequestAttributes
            CtTypeReference<?> targetTypeRef = factory.Type().createReference("org.springframework.web.context.request.ServletRequestAttributes");

            // Create the cast: (ServletRequestAttributes) invocation
            CtUnaryOperator<?> castExpression = factory.Core().createUnaryOperator();
            castExpression.setKind(UnaryOperatorKind.CAST);
            castExpression.setType(targetTypeRef);
            
            // Clone the original invocation to move it inside the cast
            castExpression.setOperand(invocation.clone());

            // Replace the original invocation with the new cast expression
            invocation.replace(castExpression);

            System.out.println("Refactored RequestContextHolder call at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by arguments)
        String inputPath = "/home/kth/Documents/last_transformer/output/4259baebb426fefbe9dbee26725d6803170dcb85/lti-launch/src/main/java/edu/ksu/lti/launch/service/LtiSessionService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4259baebb426fefbe9dbee26725d6803170dcb85/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4259baebb426fefbe9dbee26725d6803170dcb85/lti-launch/src/main/java/edu/ksu/lti/launch/service/LtiSessionService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4259baebb426fefbe9dbee26725d6803170dcb85/attempt_1/transformed");

        // CRITICAL: Configure Environment for Source Preservation (Sniper Mode)
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Set SniperJavaPrettyPrinter to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode (robustness against missing libs)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new RequestAttributesCastProcessor());

        try {
            System.out.println("Starting Spring Context Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}