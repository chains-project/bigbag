package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * Generated based on empty dependency diff.
 * This class is configured with SniperJavaPrettyPrinter for high-fidelity source code preservation.
 */
public class SpoonRefactoring {

    /**
     * Processor to handle breaking changes.
     * Edit the 'isToBeProcessed' and 'process' methods to match specific Diff logic.
     */
    public static class BreakingChangeProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            // TODO: Replace "targetMethodName" with the method name from the Diff
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"targetMethodName".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check (Optional but recommended)
            // TODO: Adjust count based on the Diff
            // if (candidate.getArguments().size() != 1) return false;

            // 3. Owner/Class Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // NOTE: In NoClasspath, declaringType might be null or incomplete.
            // We use loose string matching on the qualified name.
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // TODO: Replace "TargetClassName" with the class from the Diff
                if (!qualifiedName.contains("TargetClassName") && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Type Check (Defensive)
            // Example: Filter out if the argument is already the correct new type
            /*
            if (!candidate.getArguments().isEmpty()) {
                CtExpression<?> firstArg = candidate.getArguments().get(0);
                CtTypeReference<?> argType = firstArg.getType();
                if (argType != null && argType.getQualifiedName().contains("NewType")) {
                    return false; // Already migrated
                }
            }
            */

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // TODO: Implement the transformation logic here.
            // Example: Rename method, wrap arguments, or change imports.
            
            System.out.println("Processing invocation at: " + invocation.getPosition());
            
            // Example Transformation: Rename method
            // invocation.getExecutable().setSimpleName("newMethodName");
        }
    }

    public static void main(String[] args) {
        // Default paths - adjust as necessary
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // CRITICAL: Configure Environment for Source Preservation (Sniper Mode)
        // 1. Enable comments to preserve Javadoc and inline comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Auto-imports off usually helps Sniper preserve original structure
        launcher.getEnvironment().setAutoImports(false);

        // 3. Set the SniperJavaPrettyPrinter
        // This forces Spoon to print only changed elements, keeping the rest of the file bit-exact.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 4. NoClasspath Mode
        // Allows running without full dependency JARs. Types may be null or unknown.
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new BreakingChangeProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}