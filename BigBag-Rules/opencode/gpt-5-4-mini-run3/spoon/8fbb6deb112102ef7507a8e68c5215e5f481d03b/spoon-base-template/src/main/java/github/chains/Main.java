package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String OLD_SERVLET_PREFIX = "javax.servlet.";
    private static final String JETTY_SERVER = "org.eclipse.jetty.server";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        rewriteServletTypes(launcher);
        rewriteJettyServerApi(launcher);
        cleanupImports(launcher);

        launcher.prettyprint();
    }

    private static void rewriteServletTypes(Launcher launcher) {
        List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> ref : references) {
            String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(OLD_SERVLET_PREFIX)) {
                ref.replace(launcher.getFactory().Type().createReference("jakarta.servlet." + qn.substring(OLD_SERVLET_PREFIX.length())));
            }
        }
    }

    private static void rewriteJettyServerApi(Launcher launcher) {
        Set<CtBlock<?>> configBlocks = new HashSet<>();

        List<CtStatement> statements = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtStatement.class)));
        for (CtStatement statement : statements) {
            if (!(statement instanceof CtInvocation<?> invocation)) {
                continue;
            }

            String methodName = invocation.getExecutable().getSimpleName();
            if (!isOldServerSetter(invocation, methodName)) {
                continue;
            }

            CtBlock<?> block = statement.getParent(CtBlock.class);
            if (block == null) {
                continue;
            }

            String configName = ensureHttpConfiguration(block, statement, configBlocks, launcher);
            CtStatement replacement = launcher.getFactory().Code().createCodeSnippetStatement(configName + "." + methodName + "(" + invocation.getArguments().get(0) + ");");
            statement.replace(replacement);
        }

        List<CtConstructorCall<?>> constructorCalls = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : constructorCalls) {
            if (!isOldConnector(call)) {
                continue;
            }

            CtCodeSnippetExpression<?> replacement = launcher.getFactory().Code().createCodeSnippetExpression(
                    "new " + JETTY_SERVER + ".ServerConnector(server, new " + JETTY_SERVER + ".HttpConnectionFactory(httpConfiguration))");
            call.replace(replacement);
        }

        List<CtTypeReference<?>> typeRefs = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> ref : typeRefs) {
            if (JETTY_SERVER.concat(".Connector").equals(ref.getQualifiedName())) {
                ref.replace(launcher.getFactory().Type().createReference(JETTY_SERVER + ".NetworkConnector"));
            }
        }

        List<CtLocalVariable<?>> locals = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> local : locals) {
            if (isOldConnectorInitializer(local)) {
                local.setType(launcher.getFactory().Type().createReference(JETTY_SERVER + ".NetworkConnector"));
            }
        }
    }

    private static void cleanupImports(Launcher launcher) {
        List<CtCompilationUnit> compilationUnits = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class)));
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            for (CtImport imp : imports) {
                String qn = imp.getReference() == null ? null : imp.getReference().toString();
                if (qn == null) {
                    continue;
                }
                if (qn.startsWith(OLD_SERVLET_PREFIX) || OLD_CONNECTOR.equals(qn) || JETTY_SERVER.concat(".Connector").equals(qn)) {
                    imp.delete();
                }
            }
        }
    }

    private static boolean isOldServerSetter(CtInvocation<?> invocation, String methodName) {
        CtTypeReference<?> declaring = invocation.getExecutable().getDeclaringType();
        return declaring != null
                && JETTY_SERVER.concat(".Server").equals(declaring.getQualifiedName())
                && ("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName));
    }

    private static boolean isOldConnector(CtConstructorCall<?> call) {
        CtTypeReference<?> type = call.getType();
        return type != null && OLD_CONNECTOR.equals(type.getQualifiedName());
    }

    private static boolean isOldConnectorInitializer(CtLocalVariable<?> variable) {
        CtTypeReference<?> type = variable.getType();
        return type != null && JETTY_SERVER.concat(".Connector").equals(type.getQualifiedName())
                && variable.getDefaultExpression() instanceof CtConstructorCall<?> call
                && isOldConnector(call);
    }

    private static String ensureHttpConfiguration(CtBlock<?> block, CtStatement anchor, Set<CtBlock<?>> configBlocks, Launcher launcher) {
        if (configBlocks.contains(block)) {
            return "httpConfiguration";
        }

        String name = uniqueName(block, "httpConfiguration");
        CtCodeSnippetStatement configStmt = launcher.getFactory().Code().createCodeSnippetStatement(
                "org.eclipse.jetty.server.HttpConfiguration " + name + " = new org.eclipse.jetty.server.HttpConfiguration();");
        anchor.insertBefore(configStmt);
        configBlocks.add(block);
        return name;
    }

    private static String uniqueName(CtBlock<?> block, String base) {
        String candidate = base;
        int i = 1;
        while (hasLocalVariableNamed(block, candidate)) {
            candidate = base + i++;
        }
        return candidate;
    }

    private static boolean hasLocalVariableNamed(CtBlock<?> block, String name) {
        return block.getElements(new TypeFilter<>(CtLocalVariable.class)).stream().anyMatch(v -> name.equals(v.getSimpleName()));
    }
}
