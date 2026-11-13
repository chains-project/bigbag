package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String CONNECTOR_TYPE = "org.eclipse.jetty.server.Connector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-src-dir> <output-src-dir>");
            System.exit(1);
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.addProcessor(new JettyBlockProcessor());
        launcher.addProcessor(new ConnectorFieldProcessor());
        launcher.run();
    }

    private static final class JettyBlockProcessor extends AbstractProcessor<CtBlock<?>> {
        private final Set<CtBlock<?>> touchedBlocks = new HashSet<>();

        @Override
        public boolean isToBeProcessed(CtBlock<?> block) {
            return containsJettyBreakage(block);
        }

        @Override
        public void process(CtBlock<?> block) {
            if (!touchedBlocks.add(block)) {
                return;
            }

            CtStatement anchor = firstMatchingStatement(block);
            if (anchor != null) {
                anchor.insertBefore(getFactory().Code().createCodeSnippetStatement(
                        HTTP_CONFIGURATION + " jettyHttpConfiguration = new " + HTTP_CONFIGURATION + "();"));
            }

            rewriteInvocations(block);
            rewriteConstructors(block);
        }

        private boolean containsJettyBreakage(CtBlock<?> block) {
            for (CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
                String name = invocation.getExecutable().getSimpleName();
                if ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name)) {
                    return true;
                }
            }
            for (CtConstructorCall<?> constructorCall : block.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                CtTypeReference<?> type = constructorCall.getType();
                if (isType(type, OLD_CONNECTOR)) {
                    return true;
                }
            }
            return false;
        }

        private CtStatement firstMatchingStatement(CtBlock<?> block) {
            for (CtStatement statement : block.getStatements()) {
                if (!statement.getElements(new TypeFilter<>(CtInvocation.class)).isEmpty()) {
                    for (CtInvocation<?> invocation : statement.getElements(new TypeFilter<>(CtInvocation.class))) {
                        String name = invocation.getExecutable().getSimpleName();
                        if ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name)) {
                            return statement;
                        }
                    }
                }
                for (CtConstructorCall<?> constructorCall : statement.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                    if (isType(constructorCall.getType(), OLD_CONNECTOR)) {
                        return statement;
                    }
                }
            }

            return block.getStatements().isEmpty() ? null : block.getStatements().get(0);
        }

        private void rewriteInvocations(CtBlock<?> block) {
            for (CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
                String name = invocation.getExecutable().getSimpleName();
                if ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name)) {
                    invocation.setTarget(getFactory().Code().createCodeSnippetExpression("jettyHttpConfiguration"));
                }
            }
        }

        private void rewriteConstructors(CtBlock<?> block) {
            for (CtConstructorCall<?> constructorCall : block.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                if (!isType(constructorCall.getType(), OLD_CONNECTOR)) {
                    continue;
                }

                String serverName = findServerName(constructorCall);
                if (serverName == null) {
                    continue;
                }

                constructorCall.replace(getFactory().Code().createCodeSnippetExpression(
                        "new " + NEW_CONNECTOR + "(" + serverName + ", new " + HTTP_CONNECTION_FACTORY + "(jettyHttpConfiguration))"));
            }
        }

        private String findServerName(CtConstructorCall<?> anchor) {
            CtBlock<?> block = anchor.getParent(CtBlock.class);
            if (block != null) {
                for (CtLocalVariable<?> local : block.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                    if (isType(local.getType(), SERVER_TYPE)) {
                        return local.getSimpleName();
                    }
                }
            }

            CtType<?> type = anchor.getParent(CtType.class);
            if (type != null) {
                for (CtField<?> field : type.getFields()) {
                    if (isType(field.getType(), SERVER_TYPE)) {
                        return field.getSimpleName();
                    }
                }
            }

            return null;
        }
    }

    private static final class ConnectorFieldProcessor extends AbstractProcessor<CtField<?>> {
        @Override
        public boolean isToBeProcessed(CtField<?> field) {
            return isType(field.getType(), CONNECTOR_TYPE)
                    && field.getParent(CtType.class) != null
                    && !field.getParent(CtType.class).getElements(new TypeFilter<>(CtConstructorCall.class)).isEmpty();
        }

        @Override
        public void process(CtField<?> field) {
            field.setType(getFactory().Type().createReference(NEW_CONNECTOR));
        }
    }

    private static boolean isType(CtTypeReference<?> type, String qualifiedName) {
        return type != null && qualifiedName.equals(type.getQualifiedName());
    }
}
