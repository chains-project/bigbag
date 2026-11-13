package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-dir> [output-dir]");
            System.exit(1);
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();
        transform(launcher.getModel());
        launcher.prettyprint();
    }

    private static void transform(CtModel model) {
        List<CtLocalVariable<?>> locals = model.getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            if (isOldScriptResultType(local.getType()) && local.getDefaultExpression() instanceof CtConstructorCall<?> ctor
                    && isOldScriptResultConstructor(ctor)) {
                local.setType(local.getFactory().Type().objectType());
                local.setDefaultExpression((CtExpression) firstArgument(ctor).clone());
            }
        }

        List<CtConstructorCall<?>> constructors = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructors) {
            if (isOldScriptResultConstructor(constructorCall)) {
                constructorCall.replace((CtElement) ((CtExpression<?>) firstArgument(constructorCall).clone()));
            }
        }

        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (isGetJavaScriptResult(invocation)) {
                invocation.replace((CtElement) ((CtExpression<?>) invocation.getTarget().clone()));
            }
        }
    }

    private static boolean isGetJavaScriptResult(CtInvocation<?> invocation) {
        return "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
    }

    private static boolean isOldScriptResultConstructor(CtConstructorCall<?> constructorCall) {
        CtTypeReference<?> type = constructorCall.getType();
        return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName()) && constructorCall.getArguments().size() == 1;
    }

    private static boolean isOldScriptResultType(CtTypeReference<?> type) {
        return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CtExpression<?>> T firstArgument(CtConstructorCall<?> constructorCall) {
        return (T) constructorCall.getArguments().get(0);
    }
}
