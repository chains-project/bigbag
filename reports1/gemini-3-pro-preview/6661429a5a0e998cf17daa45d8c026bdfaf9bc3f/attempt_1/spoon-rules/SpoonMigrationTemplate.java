package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtElement;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpoonMigrationTemplate {

    /**
     * Processor to handle API changes.
     * MODIFY THIS CLASS based on your specific Diff.
     * Currently configured for: Method Rename (oldMethod -> newMethod)
     */
    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // TODO: Replace "oldMethod" with the method name from your Diff
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"oldMethod".equals(methodName)) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // CRITICAL: Handle cases where declaringType is null or unknown
            if (declaringType == null) {
                return false;
            }
            
            // Use loose string matching instead of strict equality to handle generic type erasure and missing classpath
            // TODO: Replace "com.old.Legacy" with the class from your Diff
            String qualifiedName = declaringType.getQualifiedName();
            if (!qualifiedName.contains("com.old.Legacy") && !qualifiedName.equals("<unknown>")) {
                return false;
            }

            // 3. Argument Check (Optional: Filter by argument types if overloading exists)
            // Example defensive coding for arguments:
            /*
            if (candidate.getArguments().size() > 0) {
                CtExpression<?> firstArg = candidate.getArguments().get(0);
                CtTypeReference<?> argType = firstArg.getType();
                // If type is known and doesn't match expected, skip. 
                // If type is null (unknown), we generally process it to be safe.
                if (argType != null && !argType.getQualifiedName().contains("ExpectedType")) {
                    return false;
                }
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // TODO: Implement your transformation logic here.
            // Example: Renaming the method
            invocation.getExecutable().setSimpleName("newMethod");
            
            System.out.println("Refactored method at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths - can be overridden by args
        String inputPath = "/home/kth/Documents/last_transformer/output/6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/attempt_1/transformed");

        // =========================================================
        // CRITICAL CONFIGURATION: PRESERVE FORMATTING (SNIPER MODE)
        // =========================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve whitespace/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (Defensive handling required in Processor)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new MigrationProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}