package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_OWNER = "org.reflections.util.FilterBuilder";
    private static final String OLD_METHOD = "apply";
    private static final String NEW_METHOD = "test";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new RenameFilterBuilderApplyProcessor());
        launcher.run();
    }

    private static final class RenameFilterBuilderApplyProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            return isFilterBuilderApply(invocation);
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            CtExecutableReference executable = invocation.getExecutable().clone();
            executable.setSimpleName(NEW_METHOD);
            invocation.setExecutable(executable);
        }

        private boolean isFilterBuilderApply(CtInvocation<?> invocation) {
            if (!OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }

            if (invocation.getArguments().size() != 1) {
                return false;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return false;
            }

            return isFilterBuilderChain(target);
        }

        private boolean isFilterBuilderChain(CtExpression<?> expression) {
            if (expression instanceof CtConstructorCall<?>) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
                return constructorCall.getType() != null && constructorCall.getType().getSimpleName().equals("FilterBuilder");
            }

            if (!(expression instanceof CtInvocation<?>)) {
                return false;
            }

            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            String methodName = invocation.getExecutable().getSimpleName();
            if ("include".equals(methodName) || "includePackage".equals(methodName) || "includePattern".equals(methodName)
                    || "exclude".equals(methodName) || "excludePackage".equals(methodName) || "excludePattern".equals(methodName)
                    || "add".equals(methodName)) {
                CtExpression<?> target = invocation.getTarget();
                return target != null && isFilterBuilderChain(target);
            }

            return invocation.toString().contains("FilterBuilder");
        }
    }
}
