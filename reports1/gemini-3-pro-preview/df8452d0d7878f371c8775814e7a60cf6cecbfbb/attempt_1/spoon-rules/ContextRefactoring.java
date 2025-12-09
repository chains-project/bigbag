package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtTypeCast;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class ContextRefactoring {

    /**
     * Processor to handle source incompatibility in ConfigurableApplicationContext.
     * Enforces explicit casting for getBeanFactory() to ensure type safety.
     */
    public static class BeanFactoryCastProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            // The diff explicitly lists getBeanFactory, so we target it.
            if (!"getBeanFactory".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner/Declaring Type Check (Defensive for NoClasspath)
            // We verify the method belongs to ConfigurableApplicationContext or a subtype.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains("ConfigurableApplicationContext")) {
                // If declaring type is unknown or not the target class, we skip to be safe, 
                // unless we want to be aggressive with name-only matching.
                return false;
            }

            // 4. Check if already casted
            // If the parent is already a TypeCast to ConfigurableListableBeanFactory, skip.
            if (candidate.getParent() instanceof CtTypeCast) {
                CtTypeCast<?> parentCast = (CtTypeCast<?>) candidate.getParent();
                CtTypeReference<?> castType = parentCast.getCastType();
                if (castType != null && castType.getQualifiedName().contains("ConfigurableListableBeanFactory")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Define the target type for the cast: ConfigurableListableBeanFactory
            // This is the specific return type required to resolve source incompatibility.
            CtTypeReference<?> targetTypeRef = factory.Type().createReference("org.springframework.beans.factory.config.ConfigurableListableBeanFactory");

            // Clone the original invocation to preserve its structure (arguments, etc.)
            CtExpression<?> originalExpression = invocation.clone();

            // Create the cast: (ConfigurableListableBeanFactory) invocation
            CtTypeCast<?> castExpression = factory.Code().createTypeCast(targetTypeRef, originalExpression);

            // Replace the original invocation with the casted version
            invocation.replace(castExpression);

            System.out.println("Refactored getBeanFactory call at line " + invocation.getPosition().getLine() + " to explicit cast.");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/df8452d0d7878f371c8775814e7a60cf6cecbfbb/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/df8452d0d7878f371c8775814e7a60cf6cecbfbb/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/df8452d0d7878f371c8775814e7a60cf6cecbfbb/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/df8452d0d7878f371c8775814e7a60cf6cecbfbb/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and Source Preservation
        // 1. Enable comments to ensure they are parsed
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of unchanged code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing without full dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new BeanFactoryCastProcessor());

        System.out.println("Starting refactoring with input: " + inputPath);
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}