package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_TYPE = "java.lang.Object";
    private static final String METHOD_NAME = "getJavaScriptResult";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args.length > 1 ? args[1] : "spooned-output"));
        launcher.addProcessor(new ScriptResultProcessor());
        launcher.run();
    }

    private static final class ScriptResultProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(final CtElement element) {
            if (element instanceof CtLocalVariable<?>) {
                rewriteLocalVariable((CtLocalVariable<?>) element);
            }
            if (element instanceof CtInvocation<?>) {
                rewriteInvocation((CtInvocation<?>) element);
            }
        }

        private void rewriteLocalVariable(final CtLocalVariable<?> localVariable) {
            if (!isOldType(localVariable.getType())) {
                return;
            }
            if (!(localVariable.getAssignment() instanceof CtConstructorCall<?>)) {
                return;
            }

            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) localVariable.getAssignment();
            if (!isOldType(constructorCall.getType()) || constructorCall.getArguments().size() != 1) {
                return;
            }

            localVariable.setType(localVariable.getFactory().Type().OBJECT);
            localVariable.setAssignment((CtExpression) constructorCall.getArguments().get(0).clone());
        }

        private void rewriteInvocation(final CtInvocation<?> invocation) {
            if (!METHOD_NAME.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            if (target instanceof CtConstructorCall<?>) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                if (isOldType(constructorCall.getType()) && constructorCall.getArguments().size() == 1) {
                    invocation.replace((CtExpression) constructorCall.getArguments().get(0).clone());
                }
                return;
            }

            invocation.replace((CtExpression) target.clone());
        }

        private boolean isOldType(final spoon.reflect.reference.CtTypeReference<?> type) {
            return type != null && OLD_TYPE.equals(type.getQualifiedName());
        }
    }
}
