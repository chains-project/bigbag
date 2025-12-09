package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool.
 * 
 * Generated based on the provided instructions.
 * Note: The input <dependency_change_diff> was empty.
 * This class contains a skeleton processor implementing the required
 * "Sniper" (source preservation) and "NoClasspath" (defensive types) rules.
 * 
 * Replace "TARGET_METHOD_NAME" and logic inside `process` to apply specific refactoring.
 */
public class RefactoringPipeline {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Placeholder)
            String targetMethodName = "TARGET_METHOD_NAME"; 
            if (!targetMethodName.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Type Check (Defensive for NoClasspath)
            // Example: We want to process only if the argument is NOT already the new type.
            // In NoClasspath, getType() might be null.
            if (!candidate.getArguments().isEmpty()) {
                CtExpression<?> firstArg = candidate.getArguments().get(0);
                CtTypeReference<?> argType = firstArg.getType();

                // Rule: NEVER assume getType() is non-null.
                // Rule: Use string matching for classes in NoClasspath mode.
                if (argType != null && argType.getQualifiedName().contains("NewTargetType")) {
                    // Already refactored
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // TODO: Implement transformation logic here based on the Diff.
            // Example transformation (Replacing an argument):
            /*
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            CtTypeReference<?> newTypeRef = factory.Type().createReference("com.example.NewType");
            
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(newTypeRef),
                factory.Method().createReference(newTypeRef, factory.Type().voidPrimitiveType(), "of", originalArg.getType()),
                originalArg.clone()
            );
            originalArg.replace(replacement);
            */
            
            System.out.println("Processed invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/07e4b2894bc68cd3bb1892beaa13ec353564dcf1/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/07e4b2894bc68cd3bb1892beaa13ec353564dcf1/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/07e4b2894bc68cd3bb1892beaa13ec353564dcf1/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/07e4b2894bc68cd3bb1892beaa13ec353564dcf1/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve indentation/formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive coding required in Processor)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}