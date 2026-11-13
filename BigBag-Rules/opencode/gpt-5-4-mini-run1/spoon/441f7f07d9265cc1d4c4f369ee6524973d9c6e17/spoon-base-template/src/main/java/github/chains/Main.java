package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_ACCESSOR = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path sourceDirectory = Paths.get(args[0]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDirectory.toString());
        launcher.setSourceOutputDirectory(sourceDirectory.toFile());
        launcher.buildModel();

        int[] rewritten = {0};
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isOldScriptResultAccessor(invocation)) {
                continue;
            }

            CtExpression<?> replacement = ((CtConstructorCall<?>) invocation.getTarget()).getArguments().get(0).clone();
            invocation.replace(replacement);
            rewritten[0]++;
        }

        launcher.prettyprint();
        System.out.println("Rewritten occurrences: " + rewritten[0]);
    }

    private static boolean isOldScriptResultAccessor(CtInvocation<?> invocation) {
        if (!OLD_ACCESSOR.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        CtElement target = invocation.getTarget();
        if (target instanceof CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> type = constructorCall.getType();
            return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName()) && constructorCall.getArguments().size() == 1;
        }

        return false;
    }
}
