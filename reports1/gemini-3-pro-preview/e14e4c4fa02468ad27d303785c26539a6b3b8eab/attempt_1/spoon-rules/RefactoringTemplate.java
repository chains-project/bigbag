package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RefactoringTemplate {

    /**
     * Processor to handle method refactoring (e.g., Rename, Argument Change).
     * Currently configured as a template to rename 'oldMethod' to 'newMethod'.
     */
    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {

        // TODO: Update these constants based on your Diff
        private static final String TARGET_METHOD_NAME = "oldMethod";
        private static final String NEW_METHOD_NAME = "newMethod";
        private static final String TARGET_CLASS_SUBSTRING = "TargetClassName";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // Fast fail if the method name doesn't match
            if (!TARGET_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Class Check (Defensive for NoClasspath)
            // We use relaxed string matching because in NoClasspath mode, full resolution might fail.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If declaring type is known, check if it matches the target class.
            // If it is <unknown> (null or unresolvable), we proceed cautiously or skip based on strategy.
            // Here we allow processing if it contains the name OR if we can't be sure (null),
            // relying on the method name uniqueness.
            if (declaringType != null && !declaringType.getQualifiedName().contains(TARGET_CLASS_SUBSTRING)) {
                // If we are sure it belongs to a different class, skip it.
                // Note: In NoClasspath, getQualifiedName() might return the simple name or <unknown>.
                if (!declaringType.getQualifiedName().equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument Check (Optional)
            // Example: If the method signature implies specific arg counts
            // if (candidate.getArguments().size() != 1) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Logic to perform the transformation
            CtExecutableReference<?> executable = invocation.getExecutable();
            
            // Example Transformation: Renaming the method
            executable.setSimpleName(NEW_METHOD_NAME);

            // Example Transformation: Modifying arguments (if needed)
            /*
            Factory factory = getFactory();
            CtExpression<?> firstArg = invocation.getArguments().get(0);
            CtTypeReference<?> argType = firstArg.getType();
            
            // Defensive Type Check
            if (argType != null && argType.isPrimitive()) {
                // Perform boxing or wrapping logic here
            }
            */

            System.out.println("Refactored method at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths - adjust as necessary
        String inputPath = "/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/NotificationTemplateProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14e4c4fa02468ad27d303785c26539a6b3b8eab/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/NotificationTemplateProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14e4c4fa02468ad27d303785c26539a6b3b8eab/attempt_1/transformed");

        // =========================================================
        // CRITICAL CONFIGURATION: PRESERVE FORMATTING (SNIPER MODE)
        // =========================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force the SniperJavaPrettyPrinter. 
        // This instructs Spoon to only print changes and keep the rest of the file (spaces, tabs) identical.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode
        // Allows running without full dependency JARs.
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new MethodRefactoringProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}