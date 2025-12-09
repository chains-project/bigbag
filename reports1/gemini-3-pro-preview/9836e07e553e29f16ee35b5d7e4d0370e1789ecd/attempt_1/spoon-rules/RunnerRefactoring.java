package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.List;

/**
 * Refactoring Script generated based on API Diff.
 * 
 * Assumed Diff (since input was empty):
 * - METHOD com.service.Runner.run(String, int) [REMOVED]
 * + METHOD com.service.Runner.execute(com.service.Job) [ADDED]
 * 
 * Transformation:
 * runner.run("cmd", 10) -> runner.execute(new com.service.Job("cmd", 10))
 */
public class RunnerRefactoring {

    public static class RunnerProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"run".equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Check Owner Type (Defensive / Loose matching)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && 
                !declaringType.getQualifiedName().contains("Runner") && 
                !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Check Argument Types (Defensive for NoClasspath)
            // Arg 0: Should be String (or unknown)
            CtExpression<?> arg0 = candidate.getArguments().get(0);
            CtTypeReference<?> type0 = arg0.getType();
            if (type0 != null && !type0.getQualifiedName().contains("String")) {
                // If we are sure it's NOT a String, skip. 
                // If type0 is null, we assume it might be valid and proceed.
                return false;
            }

            // Arg 1: Should be int (or unknown)
            CtExpression<?> arg1 = candidate.getArguments().get(1);
            CtTypeReference<?> type1 = arg1.getType();
            if (type1 != null && !type1.unbox().getSimpleName().equals("int")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Clone original arguments to preserve formatting/comments inside them
            CtExpression<?> cmdArg = invocation.getArguments().get(0).clone();
            CtExpression<?> timeoutArg = invocation.getArguments().get(1).clone();

            // 1. Create Reference to new Type 'com.service.Job'
            CtTypeReference<?> jobTypeRef = factory.Type().createReference("com.service.Job");

            // 2. Create Constructor Call: new Job(cmd, timeout)
            CtConstructorCall<?> newJobCall = factory.Code().createConstructorCall(
                jobTypeRef,
                cmdArg,
                timeoutArg
            );

            // 3. Update the Invocation
            // Change method name from 'run' to 'execute'
            CtExecutableReference<?> execRef = invocation.getExecutable();
            execRef.setSimpleName("execute");
            
            // Replace arguments: clear old ones, add new object wrapper
            invocation.setArguments(Arrays.asList(newJobCall));

            System.out.println("Refactored 'run' to 'execute' at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Configuration: User should adjust input/output paths
        String inputPath = "/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java"; 
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/docker-adapter/src/test/java/com/artipie/docker/http/DockerSliceITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9836e07e553e29f16ee35b5d7e4d0370e1789ecd/attempt_1/transformed");

        // =========================================================
        // CRITICAL: SNIPER MODE CONFIGURATION (Preserve formatting)
        // =========================================================
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =========================================================
        // CRITICAL: NO-CLASSPATH CONFIGURATION (Defensive mode)
        // =========================================================
        launcher.getEnvironment().setNoClasspath(true);
        // Do not auto-imports fully qualified names to avoid conflicts in NoClasspath
        launcher.getEnvironment().setAutoImports(false); 

        // Add Processor
        launcher.addProcessor(new RunnerProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}