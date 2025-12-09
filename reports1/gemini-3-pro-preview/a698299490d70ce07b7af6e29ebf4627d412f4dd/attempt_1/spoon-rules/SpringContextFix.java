package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeCast;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringContextFix {

    /**
     * Processor to handle ConfigurableApplicationContext compatibility.
     * Strategy: Explicitly cast getBeanFactory() calls to ConfigurableListableBeanFactory
     * to resolve source incompatibility issues detected in the diff.
     */
    public static class ContextProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();

            // 1. Method Name Check
            if (!"getBeanFactory".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Argument Check (getBeanFactory takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Defensive Owner Check (NoClasspath safe)
            // We check if the method belongs to ConfigurableApplicationContext
            CtExpression<?> target = candidate.getTarget();
            CtTypeReference<?> targetType = null;

            if (target != null) {
                targetType = target.getType();
            } else {
                // If target is implicit 'this', try to get the declaring class of the code
                CtType<?> parentType = candidate.getParent(CtType.class);
                if (parentType != null) {
                    targetType = parentType.getReference();
                }
            }

            // If we can't determine the type, or it's definitely not the target class, skip
            if (targetType != null && 
                !targetType.getQualifiedName().contains("ConfigurableApplicationContext") && 
                !targetType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Redundancy Check: If it is already casted, don't do it again
            if (candidate.getParent() instanceof CtTypeCast) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // The specific return type for ConfigurableApplicationContext.getBeanFactory()
            // is usually ConfigurableListableBeanFactory.
            // We add an explicit cast to fix potential source incompatibility.
            String returnTypeQualifiedName = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory";
            CtTypeReference<?> castTypeRef = factory.Type().createReference(returnTypeQualifiedName);

            // Clone the original invocation to put inside the cast
            CtInvocation<?> clonedInvocation = invocation.clone();

            // Create the cast: ((ConfigurableListableBeanFactory) context.getBeanFactory())
            CtTypeCast<?> typeCast = factory.Code().createTypeCast(castTypeRef, clonedInvocation);

            // Replace the original invocation with the casted version
            invocation.replace(typeCast);

            System.out.println("Refactored: Added explicit cast to getBeanFactory() at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/a698299490d70ce07b7af6e29ebf4627d412f4dd/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a698299490d70ce07b7af6e29ebf4627d412f4dd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/a698299490d70ce07b7af6e29ebf4627d412f4dd/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a698299490d70ce07b7af6e29ebf4627d412f4dd/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION START ---
        
        // 1. Enable Environment settings for robustness
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // 2. Enable NoClasspath mode (Defensive coding assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // 3. Force Sniper Printer (Strict Source Preservation)
        // This ensures only the modified AST nodes are reprinted, preserving formatting elsewhere.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // --- CRITICAL CONFIGURATION END ---

        launcher.addProcessor(new ContextProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}