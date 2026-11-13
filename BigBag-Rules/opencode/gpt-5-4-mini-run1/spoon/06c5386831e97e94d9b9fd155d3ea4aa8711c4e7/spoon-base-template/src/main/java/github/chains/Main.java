package github.chains;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        Path input = Paths.get(args.length > 0 ? args[0] : ".");
        Path output = Paths.get(args.length > 1 ? args[1] : input.toString());

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setSourceOutputDirectory(new File(output.toString()));
        launcher.buildModel();

        CtTypeReference<?> scriptResultType = launcher.getFactory().Type().createReference(OLD_SCRIPT_RESULT);
        CtTypeReference<Object> objectType = launcher.getFactory().Type().createReference(Object.class);

        for (CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (variable.getType() != null && scriptResultType.equals(variable.getType())
                    && variable.getDefaultExpression() instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) variable.getDefaultExpression();
                if (isScriptResultConstructor(constructorCall, scriptResultType) && !constructorCall.getArguments().isEmpty()) {
                    variable.setType(objectType);
                    variable.setDefaultExpression((CtExpression) constructorCall.getArguments().get(0).clone());
                }
            }
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isGetJavaScriptResult(invocation, scriptResultType)) {
                CtElement target = invocation.getTarget();
                if (target instanceof CtConstructorCall) {
                    CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                    if (!constructorCall.getArguments().isEmpty()) {
                        invocation.replace(constructorCall.getArguments().get(0).clone());
                    }
                }
                else if (target != null) {
                    invocation.replace(target.clone());
                }
            }
        }

        launcher.prettyprint();
    }

    private static boolean isGetJavaScriptResult(CtInvocation<?> invocation, CtTypeReference<?> scriptResultType) {
        return invocation.getExecutable() != null
                && "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())
                && invocation.getTarget() != null
                && invocation.getTarget().getType() != null
                && scriptResultType.getQualifiedName().equals(invocation.getTarget().getType().getQualifiedName());
    }

    private static boolean isScriptResultConstructor(CtConstructorCall<?> constructorCall,
            CtTypeReference<?> scriptResultType) {
        return constructorCall.getType() != null
                && scriptResultType.getQualifiedName().equals(constructorCall.getType().getQualifiedName());
    }
}
