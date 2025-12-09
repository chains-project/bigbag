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

public class StringUtilsRefactoring {

    /**
     * Processor to handle the removal of StringUtils.isEmpty(Object)
     * Strategy: Replace 'StringUtils.isEmpty(arg)' with '!StringUtils.hasLength(arg)'
     */
    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"isEmpty".equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count (isEmpty took exactly 1 argument)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Class (Defensive for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            
            // If owner is null or unknown, we must be careful. 
            // However, if we can read the qualified name, we ensure it matches StringUtils.
            if (owner != null && !owner.getQualifiedName().equals("<unknown>")) {
                if (!owner.getQualifiedName().contains("StringUtils")) {
                    return false;
                }
            } else {
                // In NoClasspath, if the owner is unknown but the method is 'isEmpty' with 1 arg,
                // it is risky to process. We skip to avoid false positives (like List.isEmpty()).
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Prepare the new method owner: org.springframework.util.StringUtils
            CtTypeReference<?> stringUtilsRef = factory.Type().createReference("org.springframework.util.StringUtils");

            // Create the replacement invocation: StringUtils.hasLength(originalArg)
            // Note: We use hasLength as it is the direct inverse logic of the old isEmpty (checking length > 0)
            CtInvocation<?> hasLengthCall = factory.Code().createInvocation(
                factory.Code().createTypeAccess(stringUtilsRef),
                factory.Method().createReference(stringUtilsRef, factory.Type().booleanPrimitiveType(), "hasLength", factory.Type().stringType()),
                originalArg.clone()
            );

            // Wrap the call in a NOT operator (!) to maintain logic parity
            // isEmpty(x) === !hasLength(x)
            CtUnaryOperator<?> replacement = factory.Core().createUnaryOperator();
            replacement.setKind(UnaryOperatorKind.NOT);
            replacement.setOperand(hasLengthCall);

            // Replace the original invocation
            invocation.replace(replacement);
            
            System.out.println("Refactored StringUtils.isEmpty at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0cdcc1f1319311f383676a89808c9b8eb190145c/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0cdcc1f1319311f383676a89808c9b8eb190145c/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Sniper Configuration
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}