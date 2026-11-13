package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {

    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";
    private static final String JETTY_CONNECTOR = "org.eclipse.jetty.server.Connector";
    private static final String JETTY_SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String JETTY_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String JETTY_SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new Jetty11MigrationProcessor());
        launcher.run();
    }

    private static final class Jetty11MigrationProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement ctElement) {
            if (ctElement instanceof CtBlock<?> block) {
                migrateBlock(block);
            }
            if (ctElement instanceof CtMethod<?> method) {
                migrateMethodSignature(method);
            }
            if (ctElement instanceof CtInvocation<?> invocation) {
                migrateConnectorInvocation(invocation);
            }
            if (ctElement instanceof CtConstructorCall<?> constructorCall) {
                migrateSelectChannelConnector(constructorCall);
            }
            if (ctElement instanceof CtLocalVariable<?> localVariable) {
                migrateOldConnectorType(localVariable);
            }
        }

        private void migrateBlock(CtBlock<?> block) {
            List<CtStatement> statements = new ArrayList<>(block.getStatements());
            CtLocalVariable<?> serverDeclaration = null;
            boolean hasHttpConfiguration = false;
            boolean hasServerCustomizer = false;
            boolean hasOldConnector = false;

            for (CtStatement statement : statements) {
                if (statement instanceof CtLocalVariable<?> localVariable) {
                    if (isType(localVariable.getType(), JETTY_SERVER)) {
                        serverDeclaration = localVariable;
                    }
                    if (isType(localVariable.getType(), JETTY_HTTP_CONFIGURATION)) {
                        hasHttpConfiguration = true;
                    }
                    if (isOldSelectChannelConnectorDecl(localVariable)) {
                        hasOldConnector = true;
                    }
                }
                if (statement instanceof CtInvocation<?> invocation && isServerCustomizerInvocation(invocation)) {
                    hasServerCustomizer = true;
                }
            }

            if (serverDeclaration == null) {
                return;
            }

            String serverName = serverDeclaration.getSimpleName();

            if (hasServerCustomizer && !hasHttpConfiguration) {
                serverDeclaration.insertAfter(getFactory().Code().createCodeSnippetStatement(
                        "org.eclipse.jetty.server.HttpConfiguration httpConfiguration = new org.eclipse.jetty.server.HttpConfiguration();"));
            }

            for (CtStatement statement : statements) {
                if (statement instanceof CtInvocation<?> invocation) {
                    rewriteServerCustomizerInvocation(invocation);
                }
                if (statement instanceof CtLocalVariable<?> localVariable && hasOldConnector && isOldSelectChannelConnectorDecl(localVariable)) {
                    rewriteSelectChannelConnectorDeclaration(localVariable, serverName, hasHttpConfiguration || hasServerCustomizer);
                }
            }
        }

        private void migrateMethodSignature(CtMethod<?> method) {
            if (!"handle".equals(method.getSimpleName())) {
                return;
            }
            List<CtParameter<?>> parameters = method.getParameters();
            if (parameters.size() != 4) {
                return;
            }
            if (!isType(parameters.get(1).getType(), "org.eclipse.jetty.server.Request")) {
                return;
            }
            parameters.get(2).setType(getFactory().Type().createReference("jakarta.servlet.http.HttpServletRequest"));
            parameters.get(3).setType(getFactory().Type().createReference("jakarta.servlet.http.HttpServletResponse"));
            Set<CtTypeReference<? extends Throwable>> thrownTypes = new HashSet<>();
            thrownTypes.add(getFactory().Type().createReference("jakarta.servlet.ServletException"));
            thrownTypes.add(getFactory().Type().createReference("java.io.IOException"));
            method.setThrownTypes(thrownTypes);
        }

        private boolean isServerCustomizerInvocation(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"setSendServerVersion".equals(methodName) && !"setSendDateHeader".equals(methodName)) {
                return false;
            }
            return invocation.getTarget() != null && isType(invocation.getTarget().getType(), JETTY_SERVER);
        }

        private void rewriteServerCustomizerInvocation(CtInvocation<?> invocation) {
            if (!isServerCustomizerInvocation(invocation)) {
                return;
            }
            invocation.setTarget(getFactory().Code().createCodeSnippetExpression("httpConfiguration"));
        }

        private boolean isOldSelectChannelConnectorDecl(CtLocalVariable<?> localVariable) {
            CtTypeReference<?> type = localVariable.getType();
            CtElement defaultExpression = localVariable.getDefaultExpression();
            return type != null
                    && JETTY_CONNECTOR.equals(type.getQualifiedName())
                    && defaultExpression instanceof CtConstructorCall<?> constructorCall
                    && isType(constructorCall.getType(), JETTY_SELECT_CHANNEL_CONNECTOR);
        }

        private void rewriteSelectChannelConnectorDeclaration(CtLocalVariable<?> localVariable, String serverName, boolean useHttpConfiguration) {
            String initializer = useHttpConfiguration
                    ? "new org.eclipse.jetty.server.ServerConnector(" + serverName + ", new org.eclipse.jetty.server.HttpConnectionFactory(httpConfiguration))"
                    : "new org.eclipse.jetty.server.ServerConnector(" + serverName + ")";
            localVariable.setType(getFactory().Type().createReference(JETTY_SERVER_CONNECTOR));
            localVariable.setDefaultExpression(getFactory().Code().createCodeSnippetExpression(initializer));
        }

        private void migrateConnectorInvocation(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"setPort".equals(methodName) && !"getLocalPort".equals(methodName)) {
                return;
            }
            if (invocation.getTarget() == null || invocation.getTarget().getType() == null) {
                return;
            }
            if (!JETTY_CONNECTOR.equals(invocation.getTarget().getType().getQualifiedName())) {
                return;
            }
            invocation.setTarget(getFactory().Code().createCodeSnippetExpression(
                    "((org.eclipse.jetty.server.ServerConnector) " + invocation.getTarget().toString() + ")"));
        }

        private void migrateSelectChannelConnector(CtConstructorCall<?> constructorCall) {
            if (!isType(constructorCall.getType(), JETTY_SELECT_CHANNEL_CONNECTOR)) {
                return;
            }
            CtBlock<?> parentBlock = constructorCall.getParent(CtBlock.class);
            if (parentBlock == null) {
                return;
            }
            List<CtStatement> statements = new ArrayList<>(parentBlock.getStatements());
            boolean useHttpConfiguration = false;
            String serverName = "server";
            for (CtStatement statement : statements) {
                if (statement instanceof CtLocalVariable<?> localVariable && isType(localVariable.getType(), JETTY_HTTP_CONFIGURATION)) {
                    useHttpConfiguration = true;
                }
                if (statement instanceof CtLocalVariable<?> localVariable && isType(localVariable.getType(), JETTY_SERVER)) {
                    serverName = localVariable.getSimpleName();
                }
                if (statement instanceof CtInvocation<?> invocation && isServerCustomizerInvocation(invocation)) {
                    useHttpConfiguration = true;
                }
            }
            constructorCall.replace(getFactory().Code().createCodeSnippetExpression(
                    useHttpConfiguration
                            ? "new org.eclipse.jetty.server.ServerConnector(" + serverName + ", new org.eclipse.jetty.server.HttpConnectionFactory(httpConfiguration))"
                            : "new org.eclipse.jetty.server.ServerConnector(" + serverName + ")"));
        }

        private void migrateOldConnectorType(CtLocalVariable<?> localVariable) {
            if (!isType(localVariable.getType(), JETTY_CONNECTOR)) {
                return;
            }
            CtElement defaultExpression = localVariable.getDefaultExpression();
            if (defaultExpression instanceof CtConstructorCall<?> constructorCall && isType(constructorCall.getType(), JETTY_SELECT_CHANNEL_CONNECTOR)) {
                localVariable.setType(getFactory().Type().createReference(JETTY_SERVER_CONNECTOR));
            }
        }

        private boolean isType(CtTypeReference<?> typeReference, String qualifiedName) {
            return typeReference != null && qualifiedName.equals(typeReference.getQualifiedName());
        }
    }
}
