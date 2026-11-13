package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.reference.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String SCRIPT_RESULT_SIMPLE_NAME = "ScriptResult";
    private static final String SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String JAVASCRIPT_RESULT_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!JAVASCRIPT_RESULT_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            CtExpression<?> replacement = null;

            if (target instanceof CtConstructorCall<?> constructorCall) {
                if (isScriptResultConstructor(constructorCall) && !constructorCall.getArguments().isEmpty()) {
                    replacement = constructorCall.getArguments().get(0).clone();
                }
            } else if (target instanceof CtVariableRead<?> variableRead
                    && variableRead.getVariable() != null
                    && variableRead.getVariable().getDeclaration() instanceof CtLocalVariable<?> localVariable) {
                CtExpression<?> assignment = localVariable.getAssignment();
                if (assignment instanceof CtConstructorCall<?> constructorCall
                        && isScriptResultConstructor(constructorCall)
                        && !constructorCall.getArguments().isEmpty()) {
                    replacement = constructorCall.getArguments().get(0).clone();
                }
            }

            if (replacement == null) {
                continue;
            }

            invocation.replace(replacement);
        }

        for (CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            CtTypeReference<?> type = variable.getType();
            if (type == null || (!SCRIPT_RESULT_TYPE.equals(type.getQualifiedName())
                    && !SCRIPT_RESULT_SIMPLE_NAME.equals(type.getSimpleName()))) {
                continue;
            }

            CtExpression<?> assignment = variable.getAssignment();
            if (assignment instanceof CtConstructorCall<?> constructorCall
                    && isScriptResultConstructor(constructorCall)
                    && !constructorCall.getArguments().isEmpty()) {
                CtExpression<?> value = constructorCall.getArguments().get(0).clone();
                variable.setType(value.getType() != null ? value.getType().clone() : launcher.getFactory().Type().OBJECT);
                variable.setAssignment(value);
            }
        }

        for (CtImport anImport : launcher.getModel().getImports()) {
            if (anImport.getReference() != null
                    && SCRIPT_RESULT_TYPE.equals(anImport.getReference().getQualifiedName())) {
                launcher.getModel().removeImport(anImport);
            }
        }

        launcher.prettyprint();
    }

    private static boolean isScriptResultConstructor(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getType() == null) {
            return false;
        }
        String qualifiedName = constructorCall.getType().getQualifiedName();
        String simpleName = constructorCall.getType().getSimpleName();
        return SCRIPT_RESULT_TYPE.equals(qualifiedName) || SCRIPT_RESULT_SIMPLE_NAME.equals(simpleName);
    }
}
