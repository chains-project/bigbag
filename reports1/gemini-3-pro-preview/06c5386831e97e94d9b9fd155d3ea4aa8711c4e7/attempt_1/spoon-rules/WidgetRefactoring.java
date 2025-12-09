package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class WidgetRefactoring {

    /**
     * Processor to handle breaking changes.
     * Hypothesis: Method 'render()' was renamed to 'draw()' in class 'Widget'.
     */
    public static class WidgetProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest check first)
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"render".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check (Optimization)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // We cannot rely on resolved types, so we check the declaring type reference.
            CtExecutableReference<?> executable = candidate.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();

            // If declaring type is null (inference failed) or unknown, we proceed with caution
            // or conservatively skip depending on strictness. Here we skip if strictly not Widget.
            if (declaringType != null && !declaringType.getQualifiedName().contains("Widget") 
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method from 'render' to 'draw'
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            
            // We modify the reference directly, which updates the call site
            executableRef.setSimpleName("draw");
            
            System.out.println("Refactored 'render' to 'draw' at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or passed as args)
        String inputPath = "/home/kth/Documents/last_transformer/output/06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/attempt_1/transformed");

        // CRITICAL: Configure Environment for Source Preservation (Sniper Mode)
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject SniperJavaPrettyPrinter for high-fidelity reproduction
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // 4. Register Processor
        launcher.addProcessor(new WidgetProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}