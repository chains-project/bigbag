package org.apache.dubbo.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RpcContextFutureRefactoring {

    public static class FutureContextProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name: setFuture
            if (!"setFuture".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count: 1
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Target Structure: Must be a chained call like RpcContext.getContext().setFuture(...)
            // In NoClasspath, analyzing variable types (e.g., ctx.setFuture) is risky and hard to refactor safely.
            // We target the explicit chain pattern common in Dubbo.
            CtExpression<?> target = candidate.getTarget();
            if (!(target instanceof CtInvocation)) {
                return false;
            }

            CtInvocation<?> targetInvocation = (CtInvocation<?>) target;

            // 4. Check Target Method: getContext
            if (!"getContext".equals(targetInvocation.getExecutable().getSimpleName())) {
                return false;
            }

            // 5. Check Declaring Type: RpcContext
            // We use loose matching for NoClasspath safety
            CtTypeReference<?> declaringType = targetInvocation.getExecutable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains("RpcContext")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Goal: RpcContext.getContext().setFuture(x) -> FutureContext.getContext().setCompatibleFuture(x)

            // 1. Create reference to the new owner class: org.apache.dubbo.rpc.FutureContext
            CtTypeReference<?> futureContextClass = factory.Type().createReference("org.apache.dubbo.rpc.FutureContext");

            // 2. Create the invocation: FutureContext.getContext()
            CtInvocation<?> newContextCall = factory.Code().createInvocation(
                factory.Code().createTypeAccess(futureContextClass),
                factory.Method().createReference(
                    futureContextClass,
                    futureContextClass, // Approximation of return type
                    "getContext"
                )
            );

            // 3. Create the final invocation: .setCompatibleFuture(arg)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                newContextCall,
                factory.Method().createReference(
                    futureContextClass,
                    factory.Type().voidPrimitiveType(),
                    "setCompatibleFuture",
                    factory.Type().createReference("java.util.concurrent.CompletableFuture") // Expected arg type
                ),
                originalArg.clone()
            );

            // 4. Replace the original invocation
            invocation.replace(replacement);
            System.out.println("Refactored RpcContext.setFuture to FutureContext.setCompatibleFuture at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/dc9a40fde9a9fee5aaec3f60695385ba539406d4/arex-agent-java/arex-instrumentation/dubbo/arex-dubbo-apache-v2/src/main/java/io/arex/inst/dubbo/apache/v2/DubboConsumerExtractor.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dc9a40fde9a9fee5aaec3f60695385ba539406d4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/dc9a40fde9a9fee5aaec3f60695385ba539406d4/arex-agent-java/arex-instrumentation/dubbo/arex-dubbo-apache-v2/src/main/java/io/arex/inst/dubbo/apache/v2/DubboConsumerExtractor.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dc9a40fde9a9fee5aaec3f60695385ba539406d4/attempt_1/transformed");

        // CRITICAL SETTINGS for Robustness
        // 1. Enable comments to preserve license headers and Javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer to strictly preserve formatting of unchanged code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FutureContextProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}