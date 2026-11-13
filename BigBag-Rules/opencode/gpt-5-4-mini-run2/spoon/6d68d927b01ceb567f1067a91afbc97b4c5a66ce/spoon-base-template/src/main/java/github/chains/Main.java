package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.util.List;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_TYPE = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String OLD_CTOR = "new com.gargoylesoftware.htmlunit.ScriptResult(java.lang.Object)";
    private static final String NEW_CTOR = "new org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult(java.lang.String)";
    private static final String GET_JS = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
        }
        launcher.buildModel();

        for (var type : launcher.getModel().getAllTypes()) {
            rewriteType(type);
        }

        if (args.length > 1) {
            launcher.prettyprint();
        }
    }

    private static void rewriteType(CtType<?> type) {
        for (CtMethod<?> method : type.getMethods()) {
            rewriteMethod(method);
        }
    }

    private static void rewriteMethod(CtMethod<?> method) {
        @SuppressWarnings("unchecked")
        List<CtLocalVariable<?>> locals = (List<CtLocalVariable<?>>) (List<?>) method.getElements(
                e -> e instanceof CtLocalVariable);
        for (CtLocalVariable<?> local : locals) {
            rewriteLocalVariable(local);
        }

        @SuppressWarnings("unchecked")
        List<CtInvocation<?>> invocations = (List<CtInvocation<?>>) (List<?>) method.getElements(
                e -> e instanceof CtInvocation);
        for (CtInvocation<?> invocation : invocations) {
            rewriteInvocation(invocation);
        }
    }

    private static void rewriteLocalVariable(CtLocalVariable<?> local) {
        CtExpression<?> assignment = local.getAssignment();
        if (!(assignment instanceof CtInvocation)) {
            return;
        }

        CtInvocation<?> invocation = (CtInvocation<?>) assignment;
        if (!isOldScriptResultCtor(invocation)) {
            return;
        }

        CtExpression<?> wrapped = invocation.getArguments().isEmpty() ? null : invocation.getArguments().get(0);
        if (wrapped == null) {
            return;
        }

        local.setType(local.getFactory().Type().OBJECT);
        @SuppressWarnings({"rawtypes", "unchecked"})
        CtExpression rawWrapped = wrapped;
        local.setAssignment(rawWrapped);
    }

    private static void rewriteInvocation(CtInvocation<?> invocation) {
        CtExecutable<?> executable = invocation.getExecutable().getDeclaration();
        if (executable == null || !GET_JS.equals(executable.getSimpleName())) {
            return;
        }

        CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (targetType == null || !OLD_TYPE.equals(targetType.getQualifiedName())) {
            return;
        }

        CtInvocation<?> parentInvocation = invocation.getParent(CtInvocation.class);
        if (parentInvocation != null && isOldScriptResultCtor(parentInvocation)) {
            CtExpression<?> arg = parentInvocation.getArguments().isEmpty() ? null : parentInvocation.getArguments().get(0);
            if (arg != null) {
                parentInvocation.replace(arg);
            }
            return;
        }

        CtStatement parentStmt = invocation.getParent(CtStatement.class);
        if (parentStmt != null) {
            parentStmt.replace(invocation.getTarget());
        }
    }

    private static boolean isOldScriptResultCtor(CtInvocation<?> invocation) {
        CtExecutable<?> executable = invocation.getExecutable().getDeclaration();
        return executable != null
                && executable.getParent(CtType.class) != null
                && OLD_TYPE.equals(executable.getParent(CtType.class).getQualifiedName())
                && OLD_CTOR.equals(executable.getSignature());
    }

    public static void rewriteDirectory(Path sourceDir) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();
        launcher.getModel().getAllTypes().forEach(Main::rewriteType);
        launcher.prettyprint();
    }
}
