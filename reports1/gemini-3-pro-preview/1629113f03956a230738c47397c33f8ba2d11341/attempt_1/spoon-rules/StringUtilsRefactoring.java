package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class StringUtilsRefactoring {

    /**
     * Processor to migrate org.springframework.util.StringUtils usage.
     * 
     * Breaking Change Detection:
     * The input diff indicates a breaking modification in `org.springframework.util.StringUtils`.
     * In Spring Framework 6.0, `StringUtils.isEmpty(Object)` was removed.
     * The standard replacement is `org.springframework.util.ObjectUtils.isEmpty(Object)`.
     */
    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String OLD_CLASS = "org.springframework.util.StringUtils";
        private static final String NEW_CLASS = "org.springframework.util.ObjectUtils";
        private static final String METHOD_NAME = "isEmpty";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (isEmpty takes 1 arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            // We need to ensure we are modifying Spring's StringUtils, not Apache Commons.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // Check via declaring type (resolves via imports in Spoon)
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                if (OLD_CLASS.equals(qName)) {
                    return true;
                }
            }

            // Check via explicit target in code (e.g. StringUtils.isEmpty(...))
            if (candidate.getTarget() instanceof CtTypeAccess) {
                CtTypeAccess<?> target = (CtTypeAccess<?>) candidate.getTarget();
                if (target.getAccessedType() != null) {
                    String targetName = target.getAccessedType().getQualifiedName();
                    // strict check to ensure we target Spring
                    if (OLD_CLASS.equals(targetName)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create reference to the replacement utility class: ObjectUtils
            CtTypeReference<?> newTypeRef = factory.Type().createReference(NEW_CLASS);
            CtTypeAccess<?> newTypeAccess = factory.Code().createTypeAccess(newTypeRef);

            // Transformation Logic
            if (invocation.getTarget() != null) {
                // Case: Explicit call `StringUtils.isEmpty(x)`
                // Replace `StringUtils` with `ObjectUtils`
                invocation.getTarget().replace(newTypeAccess);
            } else {
                // Case: Static import `isEmpty(x)`
                // Set explicit target to `ObjectUtils.isEmpty(x)` to fix the reference
                invocation.setTarget(newTypeAccess);
            }
            
            System.out.println("Refactored " + OLD_CLASS + "." + METHOD_NAME 
                + " to " + NEW_CLASS + "." + METHOD_NAME 
                + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        
        // 1. Enable comments to preserve them in output
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve code structure
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}