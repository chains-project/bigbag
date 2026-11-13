package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_CONNECTOR_API = "org.eclipse.jetty.server.Connector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        final Path input = Path.of(args[0]).toAbsolutePath().normalize();
        final Path output = Path.of(args[1]).toAbsolutePath().normalize();

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());
        launcher.buildModel();

        rewriteServletTypes(launcher);
        rewriteJettyConnectorTypes(launcher);
        rewriteJettyHandlerTypes(launcher);

        launcher.prettyprint();
    }

    private static void rewriteServletTypes(Launcher launcher) {
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            final String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(OLD_SERVLET_PREFIX)) {
                ref.replace(launcher.getFactory().Type().createReference(qn.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX)));
            }
        }
    }

    private static void rewriteJettyConnectorTypes(Launcher launcher) {
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            final String qn = ref.getQualifiedName();
            if (OLD_CONNECTOR.equals(qn)) {
                ref.replace(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            }
        }

        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (call.getType() != null && OLD_CONNECTOR.equals(call.getType().getQualifiedName())) {
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                        "new org.eclipse.jetty.server.ServerConnector(server)"));
            }
        }

        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<>(CtField.class))) {
            if (isOldConnectorType(field.getType())) {
                field.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            }
        }

        for (CtLocalVariable<?> local : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (isOldConnectorType(local.getType())) {
                local.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            }
        }
    }

    private static void rewriteJettyHandlerTypes(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            final String name = invocation.getExecutable() != null ? invocation.getExecutable().getSimpleName() : null;
            if ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name)) {
                invocation.delete();
            }
        }

        for (CtType<?> type : launcher.getModel().getElements(new TypeFilter<>(CtType.class))) {
            for (CtTypeReference<?> ref : type.getElements(new TypeFilter<>(CtTypeReference.class))) {
                final String qn = ref.getQualifiedName();
                if (qn != null && qn.startsWith(OLD_SERVLET_PREFIX)) {
                    ref.replace(launcher.getFactory().Type().createReference(qn.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX)));
                }
            }
        }
    }

    private static boolean isOldConnectorType(CtTypeReference<?> type) {
        return type != null && OLD_CONNECTOR_API.equals(type.getQualifiedName());
    }
}
