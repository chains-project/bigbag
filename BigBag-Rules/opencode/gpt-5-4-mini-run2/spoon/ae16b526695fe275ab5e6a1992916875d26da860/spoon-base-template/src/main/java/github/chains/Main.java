package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtBlock;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String CONNECTOR = "org.eclipse.jetty.server.Connector";
    private static final String SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";

    public static void main(String[] args) {
        Path inputPath = Paths.get(args.length > 0 ? args[0] : ".");
        Path outputPath = Paths.get(args.length > 1 ? args[1] : inputPath.resolveSibling(inputPath.getFileName() + "-spoon").toString());

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputPath.toString());
        launcher.setSourceOutputDirectory(outputPath.toFile());
        launcher.buildModel();

        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(Launcher launcher) {
        Factory factory = launcher.getFactory();

        List<CtElement> candidates = new ArrayList<>();
        candidates.addAll(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        candidates.addAll(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        candidates.sort(Comparator.comparingLong(Main::sourceStart));

        Map<CtBlock<?>, String> configurationNames = new HashMap<>();

        for (CtElement element : candidates) {
            if (element instanceof CtInvocation) {
                transformInvocation((CtInvocation<?>) element, factory, configurationNames);
            } else if (element instanceof CtConstructorCall) {
                transformConstructorCall((CtConstructorCall<?>) element, factory, configurationNames);
            }
        }
    }

    private static void transformInvocation(CtInvocation<?> invocation, Factory factory, Map<CtBlock<?>, String> configurationNames) {
        String methodName = invocation.getExecutable() != null ? invocation.getExecutable().getSimpleName() : "";
        String targetType = typeName(invocation.getTarget());
        String declaringType = typeName(invocation.getExecutable() != null ? invocation.getExecutable().getDeclaringType() : null);

        if (JETTY_SERVER.equals(targetType) || JETTY_SERVER.equals(declaringType)) {
            if ("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName)) {
                CtBlock<?> block = invocation.getParent(CtBlock.class);
                if (block != null) {
                    String configName = ensureConfigurationVariable(block, factory, configurationNames);
                    invocation.setTarget(factory.Code().createCodeSnippetExpression(configName));
                }
                return;
            }
        }

        if ("setPort".equals(methodName) || "getLocalPort".equals(methodName)) {
            if (CONNECTOR.equals(targetType) || CONNECTOR.equals(declaringType)) {
                castConnectorTarget(invocation, factory);
            }
        }
    }

    private static void transformConstructorCall(CtConstructorCall<?> constructorCall, Factory factory, Map<CtBlock<?>, String> configurationNames) {
        if (!SELECT_CHANNEL_CONNECTOR.equals(typeName(constructorCall.getType()))) {
            return;
        }

        CtBlock<?> block = constructorCall.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        String configName = ensureConfigurationVariable(block, factory, configurationNames);
        String serverExpression = findServerExpression(block, constructorCall);
        if (serverExpression == null || serverExpression.isEmpty()) {
            return;
        }

        String replacement = "new " + SERVER_CONNECTOR + "(" + serverExpression + ", new " + HTTP_CONNECTION_FACTORY + "(" + configName + "))";
        constructorCall.replace(factory.Code().createCodeSnippetExpression(replacement));
    }

    private static void castConnectorTarget(CtInvocation<?> invocation, Factory factory) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return;
        }
        String replacement = "((" + SERVER_CONNECTOR + ") (" + target + "))";
        invocation.setTarget(factory.Code().createCodeSnippetExpression(replacement));
    }

    private static String ensureConfigurationVariable(CtBlock<?> block, Factory factory, Map<CtBlock<?>, String> configurationNames) {
        String existing = configurationNames.get(block);
        if (existing != null) {
            return existing;
        }

        String candidate = "httpConfiguration";
        int suffix = 1;
        while (containsLocalVariableNamed(block, candidate)) {
            candidate = "httpConfiguration" + suffix++;
        }

        String declaration = HTTP_CONFIGURATION + " " + candidate + " = new " + HTTP_CONFIGURATION + "();";
        block.insertBegin(factory.Code().createCodeSnippetStatement(declaration));

        configurationNames.put(block, candidate);
        return candidate;
    }

    private static String findServerExpression(CtBlock<?> block, CtElement anchor) {
        for (CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("addConnector".equals(executableName(invocation)) && isServerType(typeName(invocation.getTarget()))) {
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    return target.toString();
                }
            }
        }

        CtInvocation<?> nearbyAddConnector = anchor.getParent(CtInvocation.class);
        while (nearbyAddConnector != null) {
            if ("addConnector".equals(executableName(nearbyAddConnector)) && isServerType(typeName(nearbyAddConnector.getTarget()))) {
                CtExpression<?> target = nearbyAddConnector.getTarget();
                if (target != null) {
                    return target.toString();
                }
            }
            nearbyAddConnector = nearbyAddConnector.getParent(CtInvocation.class);
        }

        return null;
    }

    private static boolean isServerType(String typeName) {
        return JETTY_SERVER.equals(typeName);
    }

    private static String executableName(CtInvocation<?> invocation) {
        return invocation.getExecutable() == null ? "" : invocation.getExecutable().getSimpleName();
    }

    private static String localName(CtElement element) {
        if (element instanceof CtLocalVariable) {
            return ((CtLocalVariable<?>) element).getSimpleName();
        }
        return null;
    }

    private static boolean containsLocalVariableNamed(CtBlock<?> block, String name) {
        return block.getElements(e -> name.equals(localName(e))).size() > 0;
    }

    private static String typeName(CtExpression<?> expression) {
        return expression == null ? null : typeName(expression.getType());
    }

    private static String typeName(CtTypeReference<?> type) {
        return type == null ? null : type.getQualifiedName();
    }

    private static long sourceStart(CtElement element) {
        SourcePosition position = element.getPosition();
        return position == null || position.isValidPosition() ? (position == null ? Long.MAX_VALUE : position.getSourceStart()) : Long.MAX_VALUE;
    }
}
