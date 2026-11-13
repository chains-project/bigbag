package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <inputDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        transformJavaScriptResultAccess(launcher);
        transformConstructorCalls(launcher);
        launcher.prettyprint();
    }

    private static void transformJavaScriptResultAccess(final Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (OLD_METHOD.equals(invocation.getExecutable().getSimpleName())
                    && invocation.getTarget() instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
                if (!constructorCall.getArguments().isEmpty()) {
                    invocation.replace(constructorCall.getArguments().get(0).clone());
                }
            }
        }
    }

    private static void transformConstructorCalls(final Launcher launcher) {
        List<CtConstructorCall<?>> constructorCalls = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (isOldScriptResultType(constructorCall.getType()) && !constructorCall.getArguments().isEmpty()) {
                constructorCall.replace(constructorCall.getArguments().get(0).clone());
            }
        }
    }

    private static boolean isOldScriptResultType(final CtTypeReference<?> typeReference) {
        if (typeReference == null) {
            return false;
        }
        return OLD_TYPE.equals(typeReference.getQualifiedName());
    }
}
