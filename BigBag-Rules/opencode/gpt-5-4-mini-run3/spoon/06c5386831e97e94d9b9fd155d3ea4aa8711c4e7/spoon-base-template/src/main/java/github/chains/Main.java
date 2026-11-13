package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JAVASCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        Path outputRoot = args.length > 1 ? Paths.get(args[1]) : sourceRoot.resolveSibling(sourceRoot.getFileName() + "-spooned");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceRoot.toString());
        launcher.setSourceOutputDirectory(outputRoot.toFile());
        launcher.buildModel();

        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (isOldScriptResultType(call.getType()) && !call.getArguments().isEmpty()) {
                CtExpression<?> payload = call.getArguments().get(0).clone();
                call.replace(payload);
            }
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (GET_JAVASCRIPT_RESULT.equals(invocation.getExecutable().getSimpleName())) {
                CtExpression<?> target = invocation.getTarget();
                if (target != null && isOldScriptResultType(target.getType())) {
                    invocation.replace(target.clone());
                }
            }
        }

        for (CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            rewriteLegacyVariable(variable);
        }
    }

    private static void rewriteLegacyVariable(CtLocalVariable<?> variable) {
        if (!isOldScriptResultType(variable.getType())) {
            return;
        }

        CtExpression<?> defaultTypeExpression = null;
        if (variable.getDefaultExpression() instanceof CtConstructorCall) {
            CtConstructorCall<?> call = (CtConstructorCall<?>) variable.getDefaultExpression();
            if (!call.getArguments().isEmpty()) {
                defaultTypeExpression = call.getArguments().get(0).clone();
            }
        }

        CtTypeReference<?> replacementType = defaultTypeExpression != null
                ? defaultTypeExpression.getType()
                : variable.getFactory().Type().objectType();
        variable.setType(replacementType);

        if (defaultTypeExpression != null) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            CtLocalVariable rawVariable = variable;
            rawVariable.setDefaultExpression(defaultTypeExpression);
        }
    }

    private static boolean isOldScriptResultType(CtTypeReference<?> type) {
        return type != null && OLD_SCRIPT_RESULT.equals(type.getQualifiedName());
    }
}
