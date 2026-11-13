package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String JAVAX_PREFIX = "javax.servlet";
    private static final String JAKARTA_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        transform(launcher);

        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        replaceJavaxServletTypes(launcher);
        replaceJettyConnectorTypes(launcher);
        migrateServerConfigurationMethods(launcher);
    }

    private static void replaceJavaxServletTypes(Launcher launcher) {
        List<CtTypeReference<?>> references = new ArrayList<CtTypeReference<?>>(launcher.getModel().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            if (qualifiedName.equals(JAVAX_PREFIX) || qualifiedName.startsWith(JAVAX_PREFIX + ".")) {
                reference.replace(launcher.getFactory().Type().createReference(JAKARTA_PREFIX + qualifiedName.substring(JAVAX_PREFIX.length())));
            }
        }
    }

    private static void replaceJettyConnectorTypes(Launcher launcher) {
        List<CtConstructorCall<?>> calls = new ArrayList<CtConstructorCall<?>>(launcher.getModel().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtTypeReference<?> newConnectorType = launcher.getFactory().Type().createReference(NEW_CONNECTOR);
            call.setType(newConnectorType);
            call.setArguments(resolveArgumentsForNewConnector(launcher, call));

            CtVariable<?> variable = call.getParent(CtVariable.class);
            if (variable != null) {
                variable.setType(newConnectorType);
            }
        }
    }

    private static void migrateServerConfigurationMethods(Launcher launcher) {
        Map<CtExecutable<?>, CtLocalVariable<?>> configurationByExecutable = new HashMap<CtExecutable<?>, CtLocalVariable<?>>();
        List<CtInvocation<?>> invocations = new ArrayList<CtInvocation<?>>(launcher.getModel().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class)));

        for (CtInvocation<?> invocation : invocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"setSendServerVersion".equals(methodName) && !"setSendDateHeader".equals(methodName)) {
                continue;
            }

            CtExecutable<?> executable = invocation.getParent(CtExecutable.class);
            if (executable == null) {
                continue;
            }

            CtLocalVariable<?> httpConfiguration = configurationByExecutable.get(executable);
            if (httpConfiguration == null) {
                httpConfiguration = createHttpConfiguration(launcher, invocation);
                configurationByExecutable.put(executable, httpConfiguration);
            }

            invocation.setTarget(launcher.getFactory().Code().createVariableRead(httpConfiguration.getReference(), httpConfiguration.hasModifier(ModifierKind.STATIC)));
        }

        List<CtConstructorCall<?>> constructorCalls = new ArrayList<CtConstructorCall<?>>(launcher.getModel().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : constructorCalls) {
            if (call.getType() == null || !NEW_CONNECTOR.equals(call.getType().getQualifiedName())) {
                continue;
            }

            CtExecutable<?> executable = call.getParent(CtExecutable.class);
            if (executable == null) {
                continue;
            }

            CtLocalVariable<?> httpConfiguration = configurationByExecutable.get(executable);
            if (httpConfiguration == null) {
                continue;
            }

            CtExpression<?> serverExpression = call.getArguments().isEmpty() ? inferServerExpression(launcher, call) : call.getArguments().get(0).clone();
            if (serverExpression == null) {
                continue;
            }

            CtExpression<?> httpConnectionFactory = launcher.getFactory().Code().createConstructorCall(
                    launcher.getFactory().Type().createReference(HTTP_CONNECTION_FACTORY),
                    launcher.getFactory().Code().createVariableRead(httpConfiguration.getReference(), httpConfiguration.hasModifier(ModifierKind.STATIC)));
            List<CtExpression<?>> arguments = new ArrayList<CtExpression<?>>();
            arguments.add(serverExpression);
            arguments.add(httpConnectionFactory);
            call.setArguments(arguments);
        }
    }

    private static CtLocalVariable<?> createHttpConfiguration(Launcher launcher, CtInvocation<?> anchor) {
        CtLocalVariable<?> httpConfiguration = launcher.getFactory().Code().createLocalVariable(
                launcher.getFactory().Type().createReference(HTTP_CONFIGURATION),
                "httpConfiguration",
                launcher.getFactory().Code().createConstructorCall(launcher.getFactory().Type().createReference(HTTP_CONFIGURATION)));
        anchor.insertBefore(httpConfiguration);
        return httpConfiguration;
    }

    private static List<CtExpression<?>> resolveArgumentsForNewConnector(Launcher launcher, CtConstructorCall<?> call) {
        CtExpression<?> serverExpression = inferServerExpression(launcher, call);
        List<CtExpression<?>> arguments = new ArrayList<CtExpression<?>>();
        if (serverExpression != null) {
            arguments.add(serverExpression);
        }
        return arguments;
    }

    private static CtExpression<?> inferServerExpression(Launcher launcher, CtConstructorCall<?> call) {
        CtVariable<?> variable = call.getParent(CtVariable.class);
        if (variable != null) {
            CtExpression<?> fromAddConnector = findServerExpressionFromAddConnector(call, variable.getReference());
            if (fromAddConnector != null) {
                return fromAddConnector;
            }
        }

        CtType<?> enclosingType = call.getParent(CtType.class);
        if (enclosingType != null) {
            CtExpression<?> uniqueServerAccess = findUniqueServerAccess(launcher, enclosingType);
            if (uniqueServerAccess != null) {
                return uniqueServerAccess;
            }
        }

        return null;
    }

    private static CtExpression<?> findServerExpressionFromAddConnector(CtConstructorCall<?> call, CtVariableReference<?> connectorReference) {
        CtType<?> enclosingType = call.getParent(CtType.class);
        if (enclosingType == null) {
            return null;
        }

        for (CtInvocation<?> invocation : enclosingType.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
            if (!"addConnector".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<?> argument = invocation.getArguments().get(0);
            if (!isSameVariable(argument, connectorReference)) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target != null && SERVER_TYPE.equals(target.getType() == null ? null : target.getType().getQualifiedName())) {
                return target.clone();
            }
        }

        return null;
    }

    private static CtExpression<?> findUniqueServerAccess(Launcher launcher, CtType<?> enclosingType) {
        List<CtVariable<?>> serverVariables = new ArrayList<CtVariable<?>>();
        for (CtVariable<?> variable : enclosingType.getElements(new TypeFilter<CtVariable<?>>(CtVariable.class))) {
            CtTypeReference<?> referenceType = variable.getType();
            if (referenceType != null && SERVER_TYPE.equals(referenceType.getQualifiedName())) {
                serverVariables.add(variable);
            }
        }

        if (serverVariables.size() == 1) {
            return launcher.getFactory().Code().createVariableRead(serverVariables.get(0).getReference(), serverVariables.get(0).hasModifier(ModifierKind.STATIC));
        }

        List<CtField<?>> serverFields = new ArrayList<CtField<?>>();
        for (CtField<?> field : enclosingType.getElements(new TypeFilter<CtField<?>>(CtField.class))) {
            CtTypeReference<?> referenceType = field.getType();
            if (referenceType != null && SERVER_TYPE.equals(referenceType.getQualifiedName())) {
                serverFields.add(field);
            }
        }

        if (serverFields.size() == 1) {
            return launcher.getFactory().Code().createVariableRead(serverFields.get(0).getReference(), serverFields.get(0).hasModifier(ModifierKind.STATIC));
        }

        return null;
    }

    private static boolean isSameVariable(CtExpression<?> expression, CtVariableReference<?> reference) {
        if (expression instanceof CtVariableRead) {
            return reference.equals(((CtVariableRead<?>) expression).getVariable());
        }
        return false;
    }
}
