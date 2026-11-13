package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.CtModel;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        rewriteServletTypes(model, factory);
        rewriteJettyConnectorCreations(model, factory);

        launcher.prettyprint();
        cleanupWrittenSources(outputDir);
    }

    private static void rewriteServletTypes(CtModel model, Factory factory) {
        List<CtTypeReference<?>> references = new ArrayList<>(model.getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName == null || !qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                continue;
            }

            CtTypeReference<?> replacement = factory.Type().createReference(
                    NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length()));
            reference.replace(replacement);
        }
    }

    private static void rewriteJettyConnectorCreations(CtModel model, Factory factory) {
        List<CtConstructorCall<?>> calls = new ArrayList<>(model.getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtExpression<?> serverExpression = findServerExpression(call);
            if (serverExpression == null) {
                continue;
            }

            CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                    factory.Type().createReference(NEW_CONNECTOR),
                    serverExpression.clone());
            call.replace(replacement);
        }
    }

    private static CtExpression<?> findServerExpression(CtConstructorCall<?> call) {
        CtElement parent = call.getParent();
        if (parent instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) parent;
            if ("addConnector".equals(invocation.getExecutable().getSimpleName()) && invocation.getTarget() != null) {
                return invocation.getTarget();
            }
        }

        CtStatement statement = call.getParent(CtStatement.class);
        if (statement == null) {
            return null;
        }

        CtExpression<?> boundExpression = null;
        if (statement instanceof CtAssignment) {
            boundExpression = ((CtAssignment<?, ?>) statement).getAssigned();
        }

        if (boundExpression == null) {
            return null;
        }

        CtBlock<?> block = statement.getParent(CtBlock.class);
        if (block == null) {
            return null;
        }

        List<CtStatement> statements = block.getStatements();
        int index = statements.indexOf(statement);
        if (index < 0) {
            return null;
        }

        for (int i = index + 1; i < statements.size(); i++) {
            CtStatement candidate = statements.get(i);
            List<CtInvocation<?>> invocations = new ArrayList<>(candidate.getElements(new TypeFilter<>(CtInvocation.class)));
            for (CtInvocation<?> invocation : invocations) {
                if (!"addConnector".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 1) {
                    continue;
                }

                if (sameBinding(boundExpression, invocation.getArguments().get(0)) && invocation.getTarget() != null) {
                    return invocation.getTarget();
                }
            }
        }

        return null;
    }

    private static boolean sameBinding(CtExpression<?> expected, CtExpression<?> actual) {
        if (expected == null || actual == null) {
            return false;
        }

        if (expected instanceof CtVariableAccess && actual instanceof CtVariableAccess) {
            return ((CtVariableAccess<?>) expected).getVariable().getSimpleName()
                    .equals(((CtVariableAccess<?>) actual).getVariable().getSimpleName());
        }

        return expected.toString().equals(actual.toString());
    }

    private static void cleanupWrittenSources(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clean transformed sources", e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8)
                    .replace("import org.eclipse.jetty.server.nio.SelectChannelConnector;", "import org.eclipse.jetty.server.ServerConnector;")
                    .replace("javax.servlet.", "jakarta.servlet.")
                    .replace("new SelectChannelConnector()", "new ServerConnector(server)");
            Files.writeString(path, source, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite " + path, e);
        }
    }
}
