package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

public class Main {
    private static final String RPC_CONTEXT = "org.apache.dubbo.rpc.RpcContext";
    private static final String FUTURE_CONTEXT = "org.apache.dubbo.rpc.FutureContext";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-spoon-out";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new DubboFutureProcessor());
        launcher.run();
    }

    private static final class DubboFutureProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!isTargetCall(invocation)) {
                return;
            }
            if (invocation.getArguments().size() != 1) {
                return;
            }

            String argumentText = invocation.getArguments().get(0).toString();
            CtTypeReference<?> argumentType = invocation.getArguments().get(0).getType();
            if (!isAsyncRpcResult(argumentType) || argumentText.contains("getResponseFuture()")) {
                return;
            }

            invocation.getArguments().get(0).replace(getFactory().Code().createCodeSnippetExpression(
                    "new org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter<>(" + argumentText + ".getResponseFuture())"));
        }

        private boolean isTargetCall(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            String methodName = executable == null ? null : executable.getSimpleName();
            if (!"setFuture".equals(methodName) && !"setCompatibleFuture".equals(methodName)) {
                return false;
            }

            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType == null) {
                return false;
            }

            String declaringTypeName = declaringType.getQualifiedName();
            return RPC_CONTEXT.equals(declaringTypeName) || FUTURE_CONTEXT.equals(declaringTypeName);
        }

        private boolean isAsyncRpcResult(CtTypeReference<?> typeReference) {
            if (typeReference == null) {
                return false;
            }
            String typeName = typeReference.getQualifiedName();
            return "org.apache.dubbo.rpc.AsyncRpcResult".equals(typeName)
                    || "AsyncRpcResult".equals(typeReference.getSimpleName());
        }

    }
}
