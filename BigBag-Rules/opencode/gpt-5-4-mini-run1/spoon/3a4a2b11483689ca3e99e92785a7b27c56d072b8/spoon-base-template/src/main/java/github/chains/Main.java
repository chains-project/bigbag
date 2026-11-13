package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-directory> <output-directory>");
        }

        Path inputDirectory = Path.of(args[0]);
        Path outputDirectory = Path.of(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDirectory.toString());
        launcher.addInputResource(inputDirectory.toString());
        launcher.buildModel();
        launcher.getModel().processWith(new ScriptResultVariableRewriter());
        launcher.getModel().processWith(new ScriptResultInvocationRewriter());
        launcher.prettyprint();
    }

    private static final class ScriptResultVariableRewriter extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public boolean isToBeProcessed(final CtLocalVariable<?> localVariable) {
            CtTypeReference<?> type = localVariable.getType();
            return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName())
                    && localVariable.getAssignment() instanceof CtConstructorCall;
        }

        @Override
        public void process(final CtLocalVariable<?> localVariable) {
            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) localVariable.getAssignment();
            if (constructorCall.getArguments().isEmpty()) {
                return;
            }

            localVariable.setType(localVariable.getFactory().Type().objectType());
            localVariable.setAssignment((CtExpression) constructorCall.getArguments().get(0).clone());
        }
    }

    private static final class ScriptResultInvocationRewriter extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(final CtInvocation<?> invocation) {
            if (!"getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            return invocation.getTarget() instanceof CtConstructorCall<?> constructorCall
                    && constructorCall.getType() != null
                    && OLD_SCRIPT_RESULT.equals(constructorCall.getType().getQualifiedName())
                    && !constructorCall.getArguments().isEmpty();
        }

        @Override
        public void process(final CtInvocation<?> invocation) {
            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
            invocation.replace(constructorCall.getArguments().get(0).clone());
        }
    }
}
