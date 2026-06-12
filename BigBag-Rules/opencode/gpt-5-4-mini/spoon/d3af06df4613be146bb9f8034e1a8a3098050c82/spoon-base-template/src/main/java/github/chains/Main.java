package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_SERVER = "org.eclipse.jetty.server.Server";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.Connector";
    private static final String OLD_SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String NEW_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String NEW_HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String JAVAX_PREFIX = "javax.servlet";
    private static final String JAKARTA_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : input;
        Files.createDirectories(output);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        transformServletTypes(factory);
        transformJettyTypes(factory);

        launcher.prettyprint();
    }

    private static void transformServletTypes(Factory factory) {
        for (CtTypeReference<?> ref : factory.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(JAVAX_PREFIX)) {
                ref.replace(factory.Type().createReference(qn.replace(JAVAX_PREFIX, JAKARTA_PREFIX)));
            }
        }
    }

    private static void transformJettyTypes(Factory factory) {
        Set<CtBlock<?>> configuredBlocks = new HashSet<CtBlock<?>>();

        for (CtInvocation<?> invocation : factory.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            String name = invocation.getExecutable().getSimpleName();
            String owner = invocation.getTarget() == null || invocation.getTarget().getType() == null
                    ? null : invocation.getTarget().getType().getQualifiedName();
            if (("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name)) && OLD_SERVER.equals(owner)) {
                CtBlock<?> block = invocation.getParent(CtBlock.class);
                if (block != null) {
                    ensureHttpConfiguration(block, invocation.getParent(CtStatement.class), factory, configuredBlocks);
                    invocation.setTarget(factory.Code().createCodeSnippetExpression("httpConfiguration"));
                }
            }
        }

        for (CtConstructorCall<?> call : factory.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (call.getType() != null && OLD_SELECT_CHANNEL_CONNECTOR.equals(call.getType().getQualifiedName())) {
                CtBlock<?> block = call.getParent(CtBlock.class);
                if (block != null) {
                    String serverExpr = findServerExpression(block);
                    if (serverExpr != null) {
                        ensureHttpConfiguration(block, call.getParent(CtStatement.class), factory, configuredBlocks);
                        call.replace(factory.Code().createCodeSnippetExpression(
                                "new " + NEW_SERVER_CONNECTOR + "(" + serverExpr + ", new " + NEW_HTTP_CONNECTION_FACTORY + "(httpConfiguration))"));
                    }
                }
            }
        }

        for (CtTypeReference<?> ref : factory.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_CONNECTOR.equals(ref.getQualifiedName()) && isUsedForLocalPort(ref)) {
                ref.replace(factory.Type().createReference(NEW_SERVER_CONNECTOR));
            }
        }
    }

    private static void ensureHttpConfiguration(CtBlock<?> block, CtElement anchor, Factory factory, Set<CtBlock<?>> configuredBlocks) {
        if (configuredBlocks.contains(block)) {
            return;
        }

        for (CtLocalVariable<?> local : block.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (local.getType() != null && NEW_HTTP_CONFIGURATION.equals(local.getType().getQualifiedName())) {
                configuredBlocks.add(block);
                return;
            }
        }

        CtStatement statement = factory.Code().createCodeSnippetStatement(
                "final " + NEW_HTTP_CONFIGURATION + " httpConfiguration = new " + NEW_HTTP_CONFIGURATION + "();");
        if (anchor instanceof CtStatement) {
            ((CtStatement) anchor).insertBefore(statement);
        } else {
            block.insertBegin(statement);
        }
        configuredBlocks.add(block);
    }

    private static String findServerExpression(CtBlock<?> block) {
        for (CtLocalVariable<?> local : block.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (local.getType() != null && OLD_SERVER.equals(local.getType().getQualifiedName())) {
                return local.getSimpleName();
            }
        }

        CtType<?> owner = block.getParent(CtType.class);
        if (owner != null) {
            for (CtField<?> field : owner.getFields()) {
                if (field.getType() != null && OLD_SERVER.equals(field.getType().getQualifiedName())) {
                    return "this." + field.getSimpleName();
                }
            }
        }

        return null;
    }

    private static boolean isUsedForLocalPort(CtTypeReference<?> ref) {
        CtType<?> owner = ref.getParent(CtType.class);
        if (owner == null) {
            return false;
        }

        String name = ref.getSimpleName();
        for (CtInvocation<?> invocation : owner.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getTarget() != null && name.equals(invocation.getTarget().toString())) {
                String method = invocation.getExecutable().getSimpleName();
                if ("getLocalPort".equals(method) || "setPort".equals(method)) {
                    return true;
                }
            }
        }
        return false;
    }
}
