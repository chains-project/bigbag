package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String REMOVED_BUILDER = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new RemovedDependencyGraphBuilderProcessor());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class RemovedDependencyGraphBuilderProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> invocation) {
            if (!"orElse".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            if (invocation.getArguments().size() != 1) {
                return;
            }

            CtExpression<?> fallback = invocation.getArguments().get(0);
            if (!isRemovedBuilderInstantiation(fallback)) {
                return;
            }

            CtExpression<?> replacement = extractOptionalValue(invocation);
            if (replacement != null) {
                invocation.replace(replacement.clone());
            }
        }

        private boolean isRemovedBuilderInstantiation(CtExpression<?> expression) {
            if (!(expression instanceof spoon.reflect.code.CtConstructorCall<?>)) {
                return false;
            }
            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
            CtTypeReference<?> type = constructorCall.getType();
            return type != null && REMOVED_BUILDER.equals(type.getQualifiedName());
        }

        private CtExpression<?> extractOptionalValue(CtInvocation<?> invocation) {
            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtInvocation<?>)) {
                return null;
            }

            CtInvocation<?> targetInvocation = (CtInvocation<?>) target;
            if (!"ofNullable".equals(targetInvocation.getExecutable().getSimpleName())) {
                return null;
            }
            if (targetInvocation.getArguments().size() != 1) {
                return null;
            }
            return targetInvocation.getArguments().get(0);
        }
    }
}
