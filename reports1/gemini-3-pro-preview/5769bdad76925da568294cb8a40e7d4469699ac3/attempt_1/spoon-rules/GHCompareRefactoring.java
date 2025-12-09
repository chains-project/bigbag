package org.kohsuke.github.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class GHCompareRefactoring {

    /**
     * Processor to handle the accessibility change of 'status' field in GHCompare.
     * Diff Analysis: FIELD org.kohsuke.github.GHCompare.status changes=FIELD_LESS_ACCESSIBLE(MAJOR)
     * Strategy: Replace field access 'obj.status' with getter 'obj.getStatus()'.
     */
    public static class StatusFieldProcessor extends AbstractProcessor<CtFieldRead<?>> {
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Name Check
            if (!"status".equals(candidate.getVariable().getSimpleName())) {
                return false;
            }

            // 2. Type Check (Defensive for NoClasspath)
            // We need to ensure we are modifying GHCompare.status and not some other 'status' field.
            boolean isGHCompare = false;

            // Approach A: Check the Declaring Type of the variable reference
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            if (declaringType != null && declaringType.getQualifiedName().contains("GHCompare")) {
                isGHCompare = true;
            }

            // Approach B: Check the Target expression type (expression before the dot)
            if (!isGHCompare) {
                CtExpression<?> target = candidate.getTarget();
                if (target != null) {
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType != null && targetType.getQualifiedName().contains("GHCompare")) {
                        isGHCompare = true;
                    }
                }
            }

            return isGHCompare;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            Factory factory = getFactory();
            
            // Determine return type (defensive fallback)
            CtTypeReference<?> returnType = fieldRead.getType();
            if (returnType == null) {
                // In NoClasspath, type might be unknown. Fallback to expected type.
                returnType = factory.Type().createReference("org.kohsuke.github.GHCompare.Status");
            }

            // Create method invocation: getStatus()
            CtInvocation<?> replacement = factory.Code().createInvocation(
                fieldRead.getTarget(), // Preserves 'obj' in 'obj.status', or null if implicit 'this'
                factory.Method().createReference(
                    fieldRead.getVariable().getDeclaringType(), 
                    returnType, 
                    "getStatus"
                )
            );

            // Replace the AST node
            fieldRead.replace(replacement);
            System.out.println("Refactored GHCompare.status access at line " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/5769bdad76925da568294cb8a40e7d4469699ac3/incrementals-tools/lib/src/main/java/io/jenkins/tools/incrementals/lib/UpdateChecker.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5769bdad76925da568294cb8a40e7d4469699ac3/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5769bdad76925da568294cb8a40e7d4469699ac3/incrementals-tools/lib/src/main/java/io/jenkins/tools/incrementals/lib/UpdateChecker.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5769bdad76925da568294cb8a40e7d4469699ac3/attempt_1/transformed");

        // CRITICAL SETTINGS: Preserve formatting and comments
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve original code structure
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StatusFieldProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}