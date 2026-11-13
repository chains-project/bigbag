package github.chains;

import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_ACCESSOR = "getJavaScriptResult";
    private static final String NEW_VALUE_TYPE = "java.lang.Object";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        List<CtLocalVariable<?>> variables = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> variable : variables) {
            CtTypeReference<?> type = variable.getType();
            if (type == null || !OLD_SCRIPT_RESULT_TYPE.equals(type.getQualifiedName())) {
                continue;
            }

            CtExpression<?> defaultExpression = variable.getDefaultExpression();
            if (!(defaultExpression instanceof CtConstructorCall)) {
                continue;
            }

            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
            CtTypeReference<?> constructorType = constructorCall.getType();
            if (constructorType == null || !OLD_SCRIPT_RESULT_TYPE.equals(constructorType.getQualifiedName()) || constructorCall.getArguments().size() != 1) {
                continue;
            }

            variable.setType(launcher.getFactory().createReference(NEW_VALUE_TYPE));
            variable.setDefaultExpression((CtExpression) constructorCall.getArguments().get(0).clone());
        }

        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!OLD_ACCESSOR.equals(invocation.getExecutable().getSimpleName()) || invocation.getTarget() == null) {
                continue;
            }

            CtElement target = invocation.getTarget();
            invocation.replace((CtExpression) target.clone());
            if (target instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                if (constructorCall.getArguments().size() == 1) {
                    invocation.replace(constructorCall.getArguments().get(0).clone());
                }
            }
        }
    }
}
