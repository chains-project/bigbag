package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for Spring's ConfigurableApplicationContext.
 * 
 * ANALYSIS:
 * The diff indicates that `ConfigurableApplicationContext` is source-incompatible, 
 * despite `getBeanFactory()` being marked as UNCHANGED. 
 * A common source of incompatibility in these upgrades involves return type inference 
 * or covariant return type changes that confuse the compiler in legacy code.
 * 
 * STRATEGY:
 * This processor targets `getBeanFactory()` invocations on `ConfigurableApplicationContext`.
 * To ensure source compatibility (and fix potential ambiguity errors), it explicitly 
 * casts the result to `ConfigurableListableBeanFactory`, which is the standard expected return type.
 */
public class SpringContextRefactoring {

    public static class ContextProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_CLASS = "ConfigurableApplicationContext";
        private static final String TARGET_METHOD = "getBeanFactory";
        private static final String RETURN_TYPE_Fix = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!TARGET_METHOD.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Check (getBeanFactory takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner/Declaring Type Check (Defensive for NoClasspath)
            // We check if the method belongs to ConfigurableApplicationContext
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains(TARGET_CLASS)) {
                // If declaring type is unknown, check the target expression type if available
                CtExpression<?> target = candidate.getTarget();
                if (target != null && target.getType() != null) {
                   if (!target.getType().getQualifiedName().contains(TARGET_CLASS)) {
                       return false;
                   }
                } else {
                    // If we can't resolve the type, skip to be safe, 
                    // or strictly process only if we are sure.
                    // Here we skip to avoid false positives on other getBeanFactory methods.
                    return false;
                }
            }

            // 4. Redundancy Check: Is it already cast?
            // e.g., (ConfigurableListableBeanFactory) ctx.getBeanFactory()
            if (candidate.getParent() instanceof CtUnaryOperator) {
                CtUnaryOperator<?> parentOp = (CtUnaryOperator<?>) candidate.getParent();
                if (parentOp.getKind() == UnaryOperatorKind.CAST) {
                    CtTypeReference<?> castType = parentOp.getType();
                    if (castType != null && castType.getQualifiedName().contains("ConfigurableListableBeanFactory")) {
                        return false;
                    }
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create the type reference for the explicit cast
            CtTypeReference<?> castTypeRef = factory.Type().createReference(RETURN_TYPE_Fix);

            // Create the cast expression: (ConfigurableListableBeanFactory) invocation
            // Note: We clone the invocation to detach it from the current tree before re-inserting
            CtExpression<?> castExpression = factory.Code().createTypeCast(castTypeRef, invocation.clone());

            // Replace the original invocation with the casted version
            invocation.replace(castExpression);
            
            System.out.println("Refactored: Added explicit cast to getBeanFactory at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/11c09e31119c28ea91a9777b2ce8893bca483493/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/11c09e31119c28ea91a9777b2ce8893bca483493/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/11c09e31119c28ea91a9777b2ce8893bca483493/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/11c09e31119c28ea91a9777b2ce8893bca483493/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Process without full dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new ContextProcessor());

        System.out.println("Starting Spring ConfigurableApplicationContext Refactoring...");
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}