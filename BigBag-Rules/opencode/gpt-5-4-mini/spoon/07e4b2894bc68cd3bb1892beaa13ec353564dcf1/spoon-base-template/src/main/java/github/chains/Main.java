package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtLocalVariable;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new HtmlUnitScriptResultProcessor());
        launcher.run();
    }

    private static final class HtmlUnitScriptResultProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(final CtElement element) {
            return isOldScriptResultImport(element)
                    || isOldScriptResultLocalVariable(element)
                    || isOldGetJavaScriptResultInvocation(element);
        }

        @Override
        public void process(final CtElement element) {
            if (element instanceof CtImport ctImport) {
                ctImport.delete();
            }
            else if (element instanceof CtLocalVariable<?> localVariable) {
                if (isOldScriptResultLocalVariable(localVariable)) {
                    localVariable.setType(getFactory().Type().OBJECT);
                    if (localVariable.getAssignment() instanceof CtConstructorCall<?> constructorCall
                            && isOldScriptResultConstructorCall(constructorCall)
                            && !constructorCall.getArguments().isEmpty()) {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        CtExpression assignment = constructorCall.getArguments().get(0).clone();
                        localVariable.setAssignment(assignment);
                    }
                }
            }
            else if (element instanceof CtConstructorCall<?> constructorCall) {
                if (isOldScriptResultConstructorCall(constructorCall) && !constructorCall.getArguments().isEmpty()) {
                    constructorCall.replace(constructorCall.getArguments().get(0).clone());
                }
            }
            else if (element instanceof CtInvocation<?> invocation) {
                replaceGetJavaScriptResultInvocation(invocation);
            }
        }

        private boolean isOldScriptResultImport(final CtElement element) {
            return element instanceof CtImport ctImport
                    && ctImport.getReference() != null
                    && OLD_TYPE.equals(ctImport.getReference().toString());
        }

        private boolean isOldScriptResultLocalVariable(final CtElement element) {
            return element instanceof CtLocalVariable<?> localVariable
                    && localVariable.getType() != null
                    && OLD_TYPE.equals(localVariable.getType().getQualifiedName());
        }

        private boolean isOldScriptResultConstructorCall(final CtElement element) {
            return element instanceof CtConstructorCall<?> constructorCall
                    && constructorCall.getType() != null
                    && OLD_TYPE.equals(constructorCall.getType().getQualifiedName());
        }

        private boolean isOldGetJavaScriptResultInvocation(final CtElement element) {
            if (!(element instanceof CtInvocation<?> invocation)) {
                return false;
            }

            return OLD_METHOD.equals(invocation.getExecutable().getSimpleName())
                    && invocation.getTarget() != null
                    && invocation.getTarget().getType() != null
                    && OLD_TYPE.equals(invocation.getTarget().getType().getQualifiedName());
        }

        private void replaceGetJavaScriptResultInvocation(final CtInvocation<?> invocation) {
            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            if (target instanceof CtConstructorCall<?> constructorCall && !constructorCall.getArguments().isEmpty()) {
                invocation.replace(constructorCall.getArguments().get(0).clone());
            }
            else {
                invocation.replace(target.clone());
            }
        }
    }
}
