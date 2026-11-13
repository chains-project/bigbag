package github.chains;

import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected input source directory path");
        }
        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> newConnectorType = factory.Type().createReference(NEW_CONNECTOR);
        updateImports(launcher);
        updateConstructorCalls(launcher, factory, newConnectorType);

        launcher.prettyprint();
    }

    private static void updateImports(Launcher launcher) {
        List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference && OLD_CONNECTOR.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName())) {
                ctImport.delete();
            }
        }
    }

    private static void updateConstructorCalls(Launcher launcher, Factory factory, CtTypeReference<?> newConnectorType) {
        List<CtConstructorCall<?>> calls = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> callType = call.getType();
            if (callType == null || !OLD_CONNECTOR.equals(callType.getQualifiedName())) {
                continue;
            }

            CtExpression<?> serverExpression = findServerExpression(call, launcher);
            if (serverExpression == null) {
                continue;
            }

            CtConstructorCall<?> replacement = factory.Code().createConstructorCall(newConnectorType.clone(), factory.Code().createCodeSnippetExpression(serverExpression.toString()));
            call.replace(replacement);
        }
    }

    private static CtExpression<?> findServerExpression(CtConstructorCall<?> call, Launcher launcher) {
        CtElement parent = call.getParent();
            if (parent instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) parent;
                if (isAddConnectorInvocation(invocation)) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        return target;
                    }
                }
            }

        String connectorVariable = connectorVariableName(call);
        if (connectorVariable == null) {
            return null;
        }

        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!isAddConnectorInvocation(invocation) || invocation.getArguments().isEmpty()) {
                continue;
            }
            CtExpression<?> argument = invocation.getArguments().get(0);
            if (referencesConnectorVariable(argument, connectorVariable)) {
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    return target;
                }
            }
        }

        return null;
    }

    private static boolean isAddConnectorInvocation(CtInvocation<?> invocation) {
        return invocation.getExecutable() != null
                && "addConnector".equals(invocation.getExecutable().getSimpleName())
                && invocation.getExecutable().getDeclaringType() != null
                && SERVER_TYPE.equals(invocation.getExecutable().getDeclaringType().getQualifiedName());
    }

    private static String connectorVariableName(CtConstructorCall<?> call) {
        CtElement parent = call.getParent();
        if (parent instanceof CtLocalVariable) {
            return ((CtLocalVariable<?>) parent).getSimpleName();
        }
        if (parent instanceof CtAssignment) {
            CtExpression<?> leftHandSide = ((CtAssignment<?, ?>) parent).getAssigned();
            return variableName(leftHandSide);
        }
        return null;
    }

    private static boolean referencesConnectorVariable(CtExpression<?> expression, String variableName) {
        if (expression instanceof CtVariableAccess) {
            return variableName.equals(((CtVariableAccess<?>) expression).getVariable().getSimpleName());
        }
        if (expression instanceof CtFieldRead) {
            return variableName.equals(((CtFieldRead<?>) expression).getVariable().getSimpleName());
        }
        return false;
    }

    private static String variableName(CtExpression<?> expression) {
        if (expression instanceof CtVariableAccess) {
            return ((CtVariableAccess<?>) expression).getVariable().getSimpleName();
        }
        if (expression instanceof CtFieldRead) {
            return ((CtFieldRead<?>) expression).getVariable().getSimpleName();
        }
        return null;
    }
}
