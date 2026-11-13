package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT = "getJavaScriptResult";

    public static void main(final String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));

        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new ScriptResultProcessor());
        launcher.run();
    }

    private static final class ScriptResultProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(final CtInvocation<?> invocation) {
            if (!GET_JAVASCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> replacement = unwrapOldScriptResult(invocation.getTarget());
            if (replacement != null) {
                invocation.replace(replacement);
            }
        }

        private CtExpression<?> unwrapOldScriptResult(final CtExpression<?> target) {
            if (target == null) {
                return null;
            }

            if (target instanceof CtConstructorCall) {
                return unwrapConstructorCall((CtConstructorCall<?>) target);
            }

            if (target instanceof CtVariableRead) {
                CtVariableRead<?> variableRead = (CtVariableRead<?>) target;
                CtVariable<?> declaration = variableRead.getVariable().getDeclaration();
                if (declaration != null) {
                    return unwrapConstructorCall(declaration.getDefaultExpression());
                }
            }

            return null;
        }

        private CtExpression<?> unwrapConstructorCall(final CtExpression<?> expression) {
            if (!(expression instanceof CtConstructorCall)) {
                return null;
            }

            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
            CtTypeReference<?> type = constructorCall.getType();
            if (type == null || !OLD_SCRIPT_RESULT.equals(type.getQualifiedName()) || constructorCall.getArguments().isEmpty()) {
                return null;
            }

            return constructorCall.getArguments().get(0).clone();
        }
    }
}
