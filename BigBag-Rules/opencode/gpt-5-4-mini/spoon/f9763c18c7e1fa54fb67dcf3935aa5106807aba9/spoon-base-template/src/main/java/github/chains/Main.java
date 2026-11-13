package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVA_SCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path as first argument");
        }

        Path inputPath = Path.of(args[0]);
        Path outputPath = args.length > 1 ? Path.of(args[1]) : inputPath.resolveSibling(inputPath.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        Environment environment = launcher.getEnvironment();
        environment.setAutoImports(true);

        launcher.addInputResource(inputPath.toString());
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(Main::rewriteScriptResultUsage);

        launcher.setSourceOutputDirectory(outputPath.toString());
        launcher.prettyprint();
    }

    private static void rewriteScriptResultUsage(final CtInvocation<?> invocation) {
        if (!GET_JAVA_SCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())) {
            return;
        }

        if (!(invocation.getTarget() instanceof CtConstructorCall<?> constructorCall)) {
            return;
        }

        if (!SCRIPT_RESULT_TYPE.equals(constructorCall.getType().getQualifiedName())) {
            return;
        }

        if (constructorCall.getArguments().isEmpty()) {
            return;
        }

        CtExpression<?> replacement = constructorCall.getArguments().get(0).clone();
        invocation.replace(replacement);
    }
}
