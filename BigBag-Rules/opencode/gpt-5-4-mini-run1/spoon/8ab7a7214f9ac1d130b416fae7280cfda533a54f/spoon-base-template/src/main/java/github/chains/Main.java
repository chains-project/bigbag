package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_RESULT_TYPE = "java.lang.Object";
    private static final String GET_JS_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-dir> [output-dir]");
            System.exit(1);
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addInputResource(input.toString());
        CtModel model = launcher.buildModel();

        for (CtConstructorCall<?> constructorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            rewriteConstructorCall(constructorCall);
        }

        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            rewriteGetJavaScriptResult(invocation);
        }

        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            removeOldTypeImport(ctImport);
        }

        for (CtLocalVariable<?> localVariable : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            rewriteLocalVariable(localVariable);
        }

        launcher.prettyprint();
    }

    private static void rewriteLocalVariable(CtLocalVariable<?> localVariable) {
        if (!isOldScriptResult(localVariable.getType())) {
            return;
        }

        localVariable.setType(localVariable.getFactory().Type().createReference(NEW_RESULT_TYPE));
    }

    private static void rewriteConstructorCall(CtConstructorCall<?> constructorCall) {
        CtExpression<?> replacement = unwrapScriptResultExpression(constructorCall);
        if (replacement != null) {
            constructorCall.replace(replacement.clone());
        }
    }

    private static void rewriteGetJavaScriptResult(CtInvocation<?> invocation) {
        if (!GET_JS_RESULT.equals(invocation.getExecutable().getSimpleName())) {
            return;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        if (!isOldScriptResult(declaringType)) {
            return;
        }

        CtExpression<?> replacement = unwrapScriptResultExpression(invocation);
        if (replacement != null) {
            invocation.replace(replacement.clone());
        }
    }

    private static void removeOldTypeImport(CtImport ctImport) {
        CtTypeReference<?> typeReference = ctImport.getReference() instanceof CtTypeReference<?> reference ? reference : null;
        if (!isOldScriptResult(typeReference)) {
            return;
        }

        ctImport.delete();
    }

    private static CtExpression<?> unwrapScriptResultExpression(CtElement element) {
        if (element instanceof CtInvocation<?> invocation) {
            if (GET_JS_RESULT.equals(invocation.getExecutable().getSimpleName())
                    && isOldScriptResult(invocation.getExecutable().getDeclaringType())) {
                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtConstructorCall<?> constructorCall && !constructorCall.getArguments().isEmpty()) {
                    return constructorCall.getArguments().get(0);
                }
                return target;
            }
        }

        if (element instanceof CtConstructorCall<?> constructorCall && !constructorCall.getArguments().isEmpty()) {
            if (isOldScriptResult(constructorCall.getType())) {
                return constructorCall.getArguments().get(0);
            }
        }
        return null;
    }

    private static boolean isOldScriptResult(CtTypeReference<?> typeReference) {
        return typeReference != null
                && (OLD_SCRIPT_RESULT.equals(typeReference.getQualifiedName())
                || "ScriptResult".equals(typeReference.getSimpleName()));
    }
}
