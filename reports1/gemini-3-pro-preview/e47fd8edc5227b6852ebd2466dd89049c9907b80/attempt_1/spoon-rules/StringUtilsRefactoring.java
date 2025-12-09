package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class StringUtilsRefactoring {

    /**
     * Processor to migrate org.springframework.util.StringUtils.isEmpty() 
     * to org.springframework.util.ObjectUtils.isEmpty().
     */
    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!"isEmpty".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count (isEmpty takes 1 arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner (Class containing the method)
            // Defensive coding for NoClasspath: Match by string, handle nulls
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains("org.springframework.util.StringUtils")) {
                return false;
            }

            // 4. Check if we are already using ObjectUtils (idempotency)
            CtExpression<?> target = candidate.getTarget();
            if (target != null && target.getType() != null && target.getType().getQualifiedName().contains("ObjectUtils")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // We want to change the target of the invocation from StringUtils to ObjectUtils
            // Old: StringUtils.isEmpty(arg)
            // New: ObjectUtils.isEmpty(arg)

            CtTypeReference<?> objectUtilsRef = factory.Type().createReference("org.springframework.util.ObjectUtils");
            
            // Create the new type access (the "ObjectUtils" part)
            CtExpression<?> newTarget = factory.Code().createTypeAccess(objectUtilsRef);
            
            // Replace the old target (the "StringUtils" part) with the new one
            if (invocation.getTarget() != null) {
                invocation.getTarget().replace(newTarget);
                
                // Update the executable reference to point to the new class to ensure consistency
                // (Though strictly speaking, replacing the target is often enough for source generation)
                invocation.getExecutable().setDeclaringType(objectUtilsRef);
                
                System.out.println("Refactored StringUtils.isEmpty to ObjectUtils.isEmpty at line " + invocation.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/e47fd8edc5227b6852ebd2466dd89049c9907b80/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e47fd8edc5227b6852ebd2466dd89049c9907b80/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e47fd8edc5227b6852ebd2466dd89049c9907b80/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e47fd8edc5227b6852ebd2466dd89049c9907b80/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Transformations
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (Defensive assumptions)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}