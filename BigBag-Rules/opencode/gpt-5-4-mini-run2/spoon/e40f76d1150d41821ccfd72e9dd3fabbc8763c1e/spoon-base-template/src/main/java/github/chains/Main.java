package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory and optional output directory");
        }

        String inputDirectory = args[0];
        String outputDirectory = args.length > 1 ? args[1] : inputDirectory;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.addProcessor(new ScriptResultUnwrapperProcessor());
        launcher.run();
    }

    private static final class ScriptResultUnwrapperProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) element;
                if ("getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())
                        && invocation.getTarget() instanceof CtConstructorCall) {
                    CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
                    if (isScriptResultType(constructorCall.getType()) && !constructorCall.getArguments().isEmpty()) {
                        invocation.replace(constructorCall.getArguments().get(0).clone());
                    }
                }
            }
            else if (element instanceof CtLocalVariable) {
                CtLocalVariable<?> variable = (CtLocalVariable<?>) element;
                CtTypeReference<?> variableType = variable.getType();
                CtExpression<?> assignment = variable.getAssignment();

                if (isScriptResultType(variableType) && assignment instanceof CtConstructorCall) {
                    CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) assignment;

                    if (isScriptResultType(constructorCall.getType()) && !constructorCall.getArguments().isEmpty()) {
                        variable.setType(variable.getFactory().Type().objectType());
                        variable.setAssignment((CtExpression) constructorCall.getArguments().get(0).clone());
                    }
                }
            }
        }
    }

    private static boolean isScriptResultType(CtTypeReference<?> type) {
        return type != null
                && "ScriptResult".equals(type.getSimpleName())
                && (type.getQualifiedName().endsWith(".ScriptResult") || "ScriptResult".equals(type.getQualifiedName()));
    }
}
