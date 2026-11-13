package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory path as the first argument.");
        }

        File sourceDirectory = new File(args[0]);
        if (!sourceDirectory.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDirectory);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory.getAbsolutePath());
        launcher.getEnvironment().setSourceOutputDirectory(sourceDirectory);
        launcher.buildModel();

        List<CtConstructorCall> constructorCalls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            CtInvocation<?> invocation = constructorCall.getParent(CtInvocation.class);
            if (invocation == null || !GET_JAVASCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (!SCRIPT_RESULT_TYPE.equals(constructorCall.getType().getQualifiedName())) {
                continue;
            }

            if (constructorCall.getArguments().size() != 1) {
                continue;
            }

            CtExpression<?> replacement = constructorCall.getArguments().get(0).clone();
            invocation.replace(replacement);
        }

        launcher.prettyprint();
    }
}
