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

    public static class StringUtilsIsEmptyProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: Target the removed method "isEmpty"
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"isEmpty".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check: StringUtils.isEmpty takes exactly 1 argument
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            // We check if the method belongs to "StringUtils"
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // Strategy: Check qualified name if available, otherwise check the target expression textual representation
            boolean isStringUtils = false;
            
            if (declaringType != null && declaringType.getQualifiedName() != null) {
                // Check for generic "StringUtils" to catch org.springframework.util.StringUtils
                // and avoid missing it due to partial classpath resolution
                if (declaringType.getQualifiedName().contains("StringUtils")) {
                    isStringUtils = true;
                }
            }
            
            // Fallback: Check the explicit target in the code (e.g. "StringUtils.isEmpty(...)")
            if (!isStringUtils && candidate.getTarget() != null) {
                String targetStr = candidate.getTarget().toString();
                if (targetStr.contains("StringUtils")) {
                    isStringUtils = true;
                }
            }

            return isStringUtils;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Refactoring Logic:
            // Convert: StringUtils.isEmpty(arg)
            // To:     !StringUtils.hasLength(arg)
            
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            CtExpression<?> originalTarget = invocation.getTarget();

            // 1. Create the new method invocation: StringUtils.hasLength(arg)
            // We reuse originalTarget to preserve the existing import/qualification (e.g., "StringUtils" vs "org.springframework.util.StringUtils")
            CtInvocation<?> hasLengthInvocation = factory.Code().createInvocation(
                originalTarget,
                factory.Method().createReference(
                    invocation.getExecutable().getDeclaringType(),
                    factory.Type().booleanPrimitiveType(),
                    "hasLength",
                    // Defensive: use object type if arg type is unknown in NoClasspath
                    originalArg.getType() != null ? originalArg.getType() : factory.Type().objectType()
                ),
                originalArg.clone()
            );

            // 2. Wrap the result in a NOT (!) operator to invert logic
            // isEmpty(x) == true  <==> hasLength(x) == false
            CtUnaryOperator<?> negation = factory.Code().createUnaryOperator(
                UnaryOperatorKind.NOT,
                hasLengthInvocation
            );

            // 3. Replace the original invocation with the negated expression
            invocation.replace(negation);
            
            System.out.println("Refactored StringUtils.isEmpty at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1e1de78344a89be66d2e78f7adb07a479f6677eb/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1e1de78344a89be66d2e78f7adb07a479f6677eb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1e1de78344a89be66d2e78f7adb07a479f6677eb/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1e1de78344a89be66d2e78f7adb07a479f6677eb/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive coding assumption)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsIsEmptyProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}