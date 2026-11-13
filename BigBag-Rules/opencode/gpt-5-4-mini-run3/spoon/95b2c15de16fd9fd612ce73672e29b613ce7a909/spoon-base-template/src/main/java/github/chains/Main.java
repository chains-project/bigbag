package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet.";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet.";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        applyServletNamespaceMigration(launcher);
        applyJettyConnectorMigration(launcher);

        launcher.prettyprint();
        rewriteGeneratedSources(args[1]);
    }

    private static void applyServletNamespaceMigration(Launcher launcher) {
        List<CtCompilationUnit> units = new ArrayList<CtCompilationUnit>(launcher.getModel().getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class)));
        for (CtCompilationUnit unit : units) {
            for (CtImport ctImport : new ArrayList<CtImport>(unit.getImports())) {
                CtTypeReference<?> imported = ctImport.getReference() instanceof CtTypeReference ? (CtTypeReference<?>) ctImport.getReference() : null;
                if (imported != null && imported.getQualifiedName().startsWith(OLD_SERVLET_PREFIX)) {
                    ctImport.setReference(launcher.getFactory().Type().createReference(
                            NEW_SERVLET_PREFIX + imported.getQualifiedName().substring(OLD_SERVLET_PREFIX.length())));
                }
            }
        }

        List<CtTypeReference<?>> references = new ArrayList<CtTypeReference<?>>(launcher.getModel().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(launcher.getFactory().Type().createReference(NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }
    }

    private static void applyJettyConnectorMigration(Launcher launcher) {
        List<CtCompilationUnit> units = new ArrayList<CtCompilationUnit>(launcher.getModel().getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class)));
        for (CtCompilationUnit unit : units) {
            for (CtImport ctImport : new ArrayList<CtImport>(unit.getImports())) {
                CtTypeReference<?> imported = ctImport.getReference() instanceof CtTypeReference ? (CtTypeReference<?>) ctImport.getReference() : null;
                if (imported != null && OLD_CONNECTOR.equals(imported.getQualifiedName())) {
                    ctImport.setReference(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                }
            }
        }

        List<CtConstructorCall<?>> calls = new ArrayList<CtConstructorCall<?>>(launcher.getModel().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> callType = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
            if (callType == null || !OLD_CONNECTOR.equals(callType.getQualifiedName())) {
                continue;
            }

            CtExpression<?> serverExpression = resolveServerExpression(call, launcher);
            if (serverExpression == null) {
                continue;
            }

            CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_CONNECTOR);
            CtConstructorCall<?> replacement = launcher.getFactory().Code().createConstructorCall(newType, serverExpression.clone());
            call.replace(replacement);
        }
    }

    private static CtExpression<?> resolveServerExpression(CtConstructorCall<?> call, Launcher launcher) {
        CtVariableReference<?> connectorVariable = findConnectorVariable(call);
        if (connectorVariable == null) {
            return null;
        }

        CtExecutable<?> executable = call.getParent(CtExecutable.class);
        if (executable == null) {
            return null;
        }

        List<CtInvocation<?>> invocations = new ArrayList<CtInvocation<?>>(executable.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!"addConnector".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 1) {
                continue;
            }

            CtExpression<?> argument = invocation.getArguments().get(0);
            if (!referencesSameVariable(argument, connectorVariable)) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    private static CtVariableReference<?> findConnectorVariable(CtConstructorCall<?> call) {
        CtElement parent = call.getParent();
        while (parent != null) {
            if (parent instanceof CtLocalVariable) {
                return ((CtLocalVariable<?>) parent).getReference();
            }
            if (parent instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtVariableRead) {
                    return ((CtVariableRead<?>) assigned).getVariable();
                }
                if (assigned instanceof CtFieldRead) {
                    return ((CtFieldRead<?>) assigned).getVariable();
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static boolean referencesSameVariable(CtExpression<?> expression, CtVariableReference<?> reference) {
        if (expression instanceof CtVariableRead) {
            return sameVariableReference(((CtVariableRead<?>) expression).getVariable(), reference);
        }
        if (expression instanceof CtFieldRead) {
            return sameVariableReference(((CtFieldRead<?>) expression).getVariable(), reference);
        }
        return false;
    }

    private static boolean sameVariableReference(CtVariableReference<?> left, CtVariableReference<?> right) {
        return left != null && right != null && left.equals(right);
    }

    private static void rewriteGeneratedSources(String outputDir) {
        Path root = Paths.get(outputDir);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite generated sources", e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            StringBuilder builder = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("import javax.servlet.")) {
                    continue;
                }
                builder.append(line.replace("org.eclipse.jetty.server.nio.SelectChannelConnector", NEW_CONNECTOR)
                        .replace("SelectChannelConnector", "ServerConnector")
                        .replace("new ServerConnector();", "new ServerConnector(server);")
                        .replace("javax.servlet.", "jakarta.servlet."))
                        .append('\n');
            }
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String updated = builder.toString();
            if (!content.equals(updated)) {
                Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite " + path, e);
        }
    }
}
