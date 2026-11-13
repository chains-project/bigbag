package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new ScriptResultProcessor());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class ScriptResultProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(final CtElement element) {
            if (element instanceof CtCompilationUnit compilationUnit) {
                removeOldImports(compilationUnit);
            }

            if (element instanceof CtInvocation<?> invocation) {
                replaceJavaScriptResultInvocation(invocation);
            }

            if (element instanceof CtLocalVariable<?> localVariable) {
                replaceLocalVariable(localVariable);
            }

            if (element instanceof CtConstructorCall<?> constructorCall) {
                replaceConstructorCall(constructorCall);
            }
        }

        private void replaceJavaScriptResultInvocation(final CtInvocation<?> invocation) {
            if (!"getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            if (target instanceof CtConstructorCall<?> constructorCall
                    && isOldScriptResult(constructorCall.getType())
                    && constructorCall.getArguments().size() == 1) {
                invocation.replace(constructorCall.getArguments().get(0).clone());
                return;
            }

            if (isOldScriptResult(target.getType())) {
                invocation.replace(target.clone());
            }
        }

        private void replaceLocalVariable(final CtLocalVariable<?> localVariable) {
            if (localVariable.getType() == null
                    || !isOldScriptResult(localVariable.getType())) {
                return;
            }

            CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
            if (defaultExpression == null) {
                return;
            }

            CtExpression<?> replacementExpression = null;
            if (defaultExpression instanceof CtInvocation<?> invocation
                    && "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())
                    && invocation.getTarget() instanceof CtConstructorCall<?> constructorCall
                    && isOldScriptResult(constructorCall.getType())
                    && constructorCall.getArguments().size() == 1) {
                replacementExpression = constructorCall.getArguments().get(0);
            }
            else if (defaultExpression instanceof CtConstructorCall<?> constructorCall
                    && isOldScriptResult(constructorCall.getType())
                    && constructorCall.getArguments().size() == 1) {
                replacementExpression = constructorCall.getArguments().get(0);
            }
            if (replacementExpression == null) {
                return;
            }

            localVariable.setType(getFactory().Type().objectType());
            @SuppressWarnings({"rawtypes", "unchecked"})
            CtLocalVariable rawLocalVariable = localVariable;
            rawLocalVariable.setDefaultExpression(replacementExpression.clone());
        }

        private void replaceConstructorCall(final CtConstructorCall<?> constructorCall) {
            if (!isOldScriptResult(constructorCall.getType()) || constructorCall.getArguments().size() != 1) {
                return;
            }

            constructorCall.replace(constructorCall.getArguments().get(0).clone());
        }

        private void removeOldImports(final CtCompilationUnit compilationUnit) {
            compilationUnit.getImports().removeIf(ctImport -> {
                CtReference reference = ctImport.getReference();
                return reference instanceof CtTypeReference<?> typeReference && isOldScriptResult(typeReference);
            });
        }

        private boolean isOldScriptResult(final CtTypeReference<?> typeReference) {
            return typeReference != null
                    && (OLD_SCRIPT_RESULT.equals(typeReference.getQualifiedName())
                    || "ScriptResult".equals(typeReference.getSimpleName()));
        }
    }
}
