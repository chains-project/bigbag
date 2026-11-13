package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected one source directory");
        }

        Path input = Paths.get(args[0]);
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setSourceOutputDirectory(input.toFile());
        launcher.buildModel();

        rewriteJettyConnectorUsage(launcher);
        rewriteServletNamespace(launcher);

        launcher.prettyprint();
    }

    private static void rewriteJettyConnectorUsage(Launcher launcher) {
        List<CtConstructorCall<?>> oldConnectors = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : oldConnectors) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtType<?> enclosingType = call.getParent(CtType.class);
            CtExpression<?> serverExpr = findServerExpression(enclosingType, launcher);
            if (serverExpr != null) {
                call.setType(launcher.getFactory().createReference(NEW_CONNECTOR));
                List<CtExpression<?>> args = new ArrayList<>();
                args.add(serverExpr.clone());
                args.add(launcher.getFactory().Code().createCodeSnippetExpression("new " + HTTP_CONNECTION_FACTORY + "(httpConfiguration)"));
                call.setArguments(args);
            }
        }

        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getExecutable() == null || invocation.getTarget() == null) {
                continue;
            }
            String targetType = invocation.getTarget().getType() != null ? invocation.getTarget().getType().getQualifiedName() : null;
            String method = invocation.getExecutable().getSimpleName();
            if (!SERVER_TYPE.equals(targetType)) {
                continue;
            }
            if (!"setSendServerVersion".equals(method) && !"setSendDateHeader".equals(method)) {
                continue;
            }

            invocation.setTarget(launcher.getFactory().Code().createCodeSnippetExpression("httpConfiguration"));
        }

        List<CtField<?>> fields = launcher.getModel().getElements(new TypeFilter<>(CtField.class));
        for (CtField<?> field : fields) {
            if (field.getType() == null || !"org.eclipse.jetty.server.Connector".equals(field.getType().getQualifiedName())) {
                continue;
            }
            if (hasLocalPortUse(field)) {
                field.setType(launcher.getFactory().createReference(NEW_CONNECTOR));
            }
        }

        List<CtLocalVariable<?>> locals = launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            if (local.getType() == null || !"org.eclipse.jetty.server.Connector".equals(local.getType().getQualifiedName())) {
                continue;
            }
            if (hasLocalPortUse(local)) {
                local.setType(launcher.getFactory().createReference(NEW_CONNECTOR));
            }
        }
    }

    private static CtExpression<?> findServerExpression(CtType<?> enclosingType, Launcher launcher) {
        if (enclosingType == null) {
            return null;
        }

        List<CtField<?>> fields = enclosingType.getElements(new TypeFilter<>(CtField.class));
        for (CtField<?> field : fields) {
            if (field.getType() != null && SERVER_TYPE.equals(field.getType().getQualifiedName())) {
                return launcher.getFactory().Code().createVariableRead(field.getReference(), false);
            }
        }

        List<CtParameter<?>> parameters = enclosingType.getElements(new TypeFilter<>(CtParameter.class));
        for (CtParameter<?> parameter : parameters) {
            if (parameter.getType() != null && SERVER_TYPE.equals(parameter.getType().getQualifiedName())) {
                return launcher.getFactory().Code().createVariableRead(parameter.getReference(), false);
            }
        }

        return null;
    }

    private static boolean hasLocalPortUse(CtField<?> field) {
        CtType<?> owner = field.getParent(CtType.class);
        return owner != null && !owner.getElements(new TypeFilter<>(CtInvocation.class)).isEmpty();
    }

    private static boolean hasLocalPortUse(CtLocalVariable<?> local) {
        CtType<?> owner = local.getParent(CtType.class);
        return owner != null && !owner.getElements(new TypeFilter<>(CtInvocation.class)).isEmpty();
    }

    private static void rewriteServletNamespace(Launcher launcher) {
        List<CtTypeReference<?>> references = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : references) {
            if (reference == null || reference.getQualifiedName() == null) {
                continue;
            }
            if (!reference.getQualifiedName().startsWith("javax.servlet")) {
                continue;
            }
            int lastDot = reference.getQualifiedName().lastIndexOf('.');
            if (lastDot <= 0) {
                continue;
            }
            String packageName = reference.getQualifiedName().substring(0, lastDot).replaceFirst("^javax\\.servlet", "jakarta.servlet");
            CtPackageReference packageReference = launcher.getFactory().Package().createReference(packageName);
            reference.setPackage(packageReference);
            reference.setSimpleName(reference.getQualifiedName().substring(lastDot + 1));
        }
    }
}
