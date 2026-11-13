package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String JAVA_SCRIPT_RESULT_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        removeOldImports(launcher);
        rewriteScriptResultConstructors(launcher);
        rewriteJavaScriptResultCalls(launcher);

        launcher.prettyprint();
    }

    private static void rewriteScriptResultConstructors(Launcher launcher) {
        List<CtConstructorCall<?>> constructorCalls = launcher.getModel().getElements(
                new TypeFilter<>(CtConstructorCall.class));

        for (CtConstructorCall<?> call : constructorCalls) {
            if (!isOldScriptResult(call.getType())) {
                continue;
            }

            CtElement parent = call.getParent();
            if (parent instanceof CtLocalVariable<?> localVariable) {
                localVariable.setType(launcher.getFactory().Type().objectType());
                CtExpression<?> argument = firstArgument(call);
                if (argument != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    CtExpression rawExpression = argument.clone();
                    localVariable.setDefaultExpression(rawExpression);
                }
            }
        }
    }

    private static void rewriteJavaScriptResultCalls(Launcher launcher) {
        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));

        for (CtInvocation<?> invocation : invocations) {
            if (!JAVA_SCRIPT_RESULT_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                continue;
            }

            if (target instanceof CtConstructorCall<?> constructorCall && isOldScriptResult(constructorCall.getType())) {
                CtExpression<?> argument = firstArgument(constructorCall);
                if (argument != null) {
                    invocation.replace(argument.clone());
                }
                continue;
            }

            invocation.replace(target.clone());
        }
    }

    private static void removeOldImports(Launcher launcher) {
        List<CtImport> imports = launcher.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference<?> reference
                    && OLD_SCRIPT_RESULT.equals(reference.getQualifiedName())) {
                ctImport.delete();
            }
        }
    }

    private static boolean isOldScriptResult(CtTypeReference<?> typeReference) {
        return typeReference != null && OLD_SCRIPT_RESULT.equals(typeReference.getQualifiedName());
    }

    private static CtExpression<?> firstArgument(CtConstructorCall<?> call) {
        return call.getArguments().isEmpty() ? null : call.getArguments().get(0);
    }
}
