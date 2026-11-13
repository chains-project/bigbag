package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtTypedElement;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(final CtImport ctImport) {
                if (ctImport.getReference() != null
                        && OLD_SCRIPT_RESULT.equals(ctImport.getReference().toString())) {
                    ctImport.delete();
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(final CtInvocation<?> invocation) {
                CtExpression<?> replacement = unwrap(invocation);
                if (replacement != null) {
                    invocation.replace(replacement.clone());
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(final CtConstructorCall<?> constructorCall) {
                if (isOldScriptResult(constructorCall)) {
                    CtExpression<?> replacement = firstArgument(constructorCall);
                    if (replacement != null) {
                        constructorCall.replace(replacement.clone());
                    }
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtLocalVariable<?>>() {
            @Override
            public void process(final CtLocalVariable<?> variable) {
                normalizeTypedElement(variable);
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtField<?>>() {
            @Override
            public void process(final CtField<?> field) {
                normalizeTypedElement(field);
            }
        });

        launcher.run();
    }

    private static CtExpression<?> unwrap(final CtExpression<?> expression) {
        if (expression == null) {
            return null;
        }

        if (expression instanceof CtInvocation<?>) {
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            if (OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtConstructorCall<?> && isOldScriptResult((CtConstructorCall<?>) target)) {
                    return firstArgument((CtConstructorCall<?>) target);
                }
                return target;
            }
        }

        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void normalizeTypedElement(final CtTypedElement element) {
        if (element.getType() == null || !OLD_SCRIPT_RESULT.equals(element.getType().getQualifiedName())) {
            return;
        }

        CtExpression<?> init = null;
        if (element instanceof CtLocalVariable<?>) {
            init = ((CtLocalVariable<?>) element).getDefaultExpression();
        }
        else if (element instanceof CtField<?>) {
            init = ((CtField<?>) element).getDefaultExpression();
        }

        CtExpression<?> replacement = unwrap(init);
        if (replacement == null) {
            return;
        }

        element.setType(replacement.getType() != null ? replacement.getType().clone() : element.getFactory().Type().OBJECT);
        if (element instanceof CtLocalVariable<?>) {
            ((CtLocalVariable) element).setDefaultExpression(replacement.clone());
        }
        else if (element instanceof CtField<?>) {
            ((CtField) element).setDefaultExpression(replacement.clone());
        }
    }

    private static boolean isOldScriptResult(final CtConstructorCall<?> constructorCall) {
        return constructorCall.getType() != null && OLD_SCRIPT_RESULT.equals(constructorCall.getType().getQualifiedName());
    }

    private static CtExpression<?> firstArgument(final CtConstructorCall<?> constructorCall) {
        return constructorCall.getArguments().isEmpty() ? null : constructorCall.getArguments().get(0);
    }
}
