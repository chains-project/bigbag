package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.FileSystemFolder;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_SCRIPT_RESULT = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-src-dir> <output-dir>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.addInputResource(new FileSystemFolder(input.toFile()));
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        for (CtLocalVariable<?> local : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            CtExpression<?> init = local.getDefaultExpression();
            if (init instanceof CtConstructorCall && hasQualifiedType(init.getType(), OLD_SCRIPT_RESULT)) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) init;
                if (!constructorCall.getArguments().isEmpty()) {
                    CtExpression<?> argument = constructorCall.getArguments().get(0);
                    ((CtLocalVariable) local).setDefaultExpression((CtExpression) argument.clone());
                    if (hasQualifiedType(local.getType(), OLD_SCRIPT_RESULT)) {
                        local.setType(argument.getType() != null ? argument.getType() : local.getFactory().Type().OBJECT);
                    }
                }
            }
        }

        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (hasQualifiedType(constructorCall.getType(), OLD_SCRIPT_RESULT) && !constructorCall.getArguments().isEmpty()) {
                constructorCall.replace(constructorCall.getArguments().get(0).clone());
            }
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !GET_JAVASCRIPT_RESULT.equals(executable.getSimpleName())) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null || !hasQualifiedType(target.getType(), OLD_SCRIPT_RESULT)) {
                continue;
            }

            invocation.replace((CtExpression) target.clone());
        }

        launcher.prettyprint();
    }

    private static boolean hasQualifiedType(CtTypeReference<?> type, String qualifiedName) {
        return type != null && qualifiedName.equals(type.getQualifiedName());
    }
}
