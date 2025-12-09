package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Template.
 * Generated because no specific Diff was provided.
 * 
 * Rules applied:
 * 1. Sniper Mode (Preserves formatting/comments).
 * 2. NoClasspath Safe (Defensive null checks on types).
 * 3. Generic Safety (Wildcards).
 */
public class RefactoringTemplate {

    public static class TemplateProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // TODO: Update these constants based on your actual Diff
        private static final String TARGET_METHOD_NAME = "oldMethodName";
        private static final String TARGET_OWNER_CLASS = "OldClassName";
        private static final String NEW_METHOD_NAME = "newMethodName";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive: NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // We use string contains/equals to handle cases where types are not fully resolved
            if (owner != null && 
                !owner.getQualifiedName().contains(TARGET_OWNER_CLASS) && 
                !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Argument/Type Check (Example: Filter out already fixed calls)
            // NEVER assume arg.getType() is not null in NoClasspath mode.
            /*
            if (!candidate.getArguments().isEmpty()) {
                CtExpression<?> arg = candidate.getArguments().get(0);
                CtTypeReference<?> type = arg.getType();
                if (type != null && type.getQualifiedName().contains("NewType")) {
                    return false; // Already migrated
                }
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Example Transformation: Rename the method
            // In a real scenario, you might wrap arguments or change types here.
            invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
            
            System.out.println("Refactored invocation at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/7e8c62e2bb21097e563747184636cf8e8934ce98/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7e8c62e2bb21097e563747184636cf8e8934ce98/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7e8c62e2bb21097e563747184636cf8e8934ce98/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7e8c62e2bb21097e563747184636cf8e8934ce98/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Preserves strict indentation/code structure)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TemplateProcessor());
        
        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}