package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtTypeCast;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ContextRefactoring {

    /**
     * Processor to handle Source Incompatibility for ConfigurableApplicationContext.
     * Strategy: Add explicit casts to getBeanFactory() calls to ensure type resolution.
     */
    public static class BeanFactoryRefactoring extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String METHOD_NAME = "getBeanFactory";
        private static final String TARGET_CLASS = "ConfigurableApplicationContext";
        private static final String RETURN_TYPE = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Check (getBeanFactory takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // We check if the method belongs to ConfigurableApplicationContext
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            boolean isConfigurableContext = declaringType != null && 
                                            declaringType.getQualifiedName().contains(TARGET_CLASS);
            
            // Also check the target expression type if available
            if (!isConfigurableContext && candidate.getTarget() != null) {
                CtTypeReference<?> targetType = candidate.getTarget().getType();
                if (targetType != null && targetType.getQualifiedName().contains(TARGET_CLASS)) {
                    isConfigurableContext = true;
                }
            }

            if (!isConfigurableContext) {
                return false;
            }

            // 4. Redundancy Check
            // If the invocation is already wrapped in a cast, we might skip it 
            // (unless we want to enforce the specific cast, but we'll assume existing casts are manual fixes).
            if (candidate.getParent() instanceof CtTypeCast) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create reference to the explicit return type
            CtTypeReference<?> returnTypeRef = factory.Type().createReference(RETURN_TYPE);

            // Clone the original invocation to safely reparent it
            CtInvocation<?> clonedInvocation = invocation.clone();

            // Create the cast: (ConfigurableListableBeanFactory) context.getBeanFactory()
            CtTypeCast<?> cast = factory.Code().createTypeCast(returnTypeRef, clonedInvocation);

            // Replace the original invocation with the casted version
            invocation.replace(cast);
            
            System.out.println("Applied explicit cast for source compatibility at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/17778ff224c38fe03999cac8caa1814b68fd0ef2/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17778ff224c38fe03999cac8caa1814b68fd0ef2/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/17778ff224c38fe03999cac8caa1814b68fd0ef2/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17778ff224c38fe03999cac8caa1814b68fd0ef2/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new BeanFactoryRefactoring());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}