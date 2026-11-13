package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVER_CONFIG = "org.eclipse.jetty.server.Server";
    private static final String NEW_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String NEW_HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path input = Paths.get(args[0]);
        final Path output = args.length == 2
                ? Paths.get(args[1])
                : input.resolveSibling(input.getFileName().toString() + "-fixed");

        try {
            Files.createDirectories(output);
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot create output directory " + output, e);
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
        launcher.addInputResource(input.toString());
        launcher.buildModel();

        transform(launcher.getModel(), launcher);
        launcher.prettyprint();
        normalizePrintedSources(output);
    }

    private static void transform(final CtModel model, final Launcher launcher) {
        fixTypeReferences(model, launcher);
        fixConnectorConstruction(model, launcher);
        fixServerConfiguration(model, launcher);
    }

    private static void fixTypeReferences(final CtModel model, final Launcher launcher) {
        final List<spoon.reflect.reference.CtTypeReference<?>> references = new ArrayList<>(
                model.getElements(new TypeFilter<>(spoon.reflect.reference.CtTypeReference.class)));

        for (final spoon.reflect.reference.CtTypeReference<?> reference : references) {
            final String qualifiedName = reference.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }

            final String replacement = toReplacementType(qualifiedName);
            if (replacement != null) {
                reference.replace(launcher.getFactory().Type().createReference(replacement));
            }
        }
    }

    private static String toReplacementType(final String qualifiedName) {
        if (qualifiedName.equals(OLD_CONNECTOR)) {
            return NEW_CONNECTOR;
        }

        if (qualifiedName.startsWith(OLD_SERVLET_PREFIX + ".")) {
            return NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length());
        }

        return null;
    }

    private static void fixConnectorConstruction(final CtModel model, final Launcher launcher) {
        final List<CtConstructorCall<?>> calls = new ArrayList<>(model.getElements(new TypeFilter<>(CtConstructorCall.class)));

        for (final CtConstructorCall<?> call : calls) {
            if (!OLD_CONNECTOR.equals(call.getType().getQualifiedName())) {
                continue;
            }

            final CtExpression<?> serverExpression = findServerExpression(call, launcher);
            if (serverExpression == null) {
                continue;
            }

            final CtConstructorCall<?> replacement = launcher.getFactory().Code()
                    .createConstructorCall(launcher.getFactory().Type().createReference(NEW_CONNECTOR), serverExpression.clone());
            call.replace(replacement);
        }
    }

    private static void fixServerConfiguration(final CtModel model, final Launcher launcher) {
        final List<spoon.reflect.code.CtInvocation<?>> invocations = new ArrayList<>(
                model.getElements(new TypeFilter<>(spoon.reflect.code.CtInvocation.class)));

        for (final spoon.reflect.code.CtInvocation<?> invocation : invocations) {
            final String simple = invocation.getExecutable().getSimpleName();
            if (!"setSendServerVersion".equals(simple) && !"setSendDateHeader".equals(simple)) {
                continue;
            }

            final CtExpression<?> target = invocation.getTarget();
            if (target == null || !OLD_SERVER_CONFIG.equals(target.getType().getQualifiedName())) {
                continue;
            }

            final CtType<?> ownerType = invocation.getParent(CtType.class);
            if (ownerType == null) {
                continue;
            }

            final String httpConfigName = uniqueName(ownerType, "httpConfiguration");
            final String connectionFactoryName = uniqueName(ownerType, "httpConnectionFactory");

            final String configSource = "final " + NEW_HTTP_CONFIGURATION + " " + httpConfigName
                    + " = new " + NEW_HTTP_CONFIGURATION + "();\n"
                    + httpConfigName + "." + simple + "(" + invocation.getArguments().get(0).toString() + ");";
            final String connectorSource = "this.httpConnector = new " + NEW_CONNECTOR + "(server, new "
                    + NEW_HTTP_CONNECTION_FACTORY + "(" + httpConfigName + "))";

            invocation.replace(launcher.getFactory().Code().createCodeSnippetStatement(configSource));
            final List<spoon.reflect.code.CtInvocation<?>> siblingInvocations = new ArrayList<>(
                    ownerType.getElements(new TypeFilter<>(spoon.reflect.code.CtInvocation.class)));
            for (final spoon.reflect.code.CtInvocation<?> sibling : siblingInvocations) {
                if (sibling == invocation) {
                    continue;
                }
            }
            final spoon.reflect.code.CtStatement assignment = launcher.getFactory().Code().createCodeSnippetStatement(connectorSource + ";");
            invocation.getParent(spoon.reflect.code.CtBlock.class).insertAfter(assignment);
        }
    }

    private static CtExpression<?> findServerExpression(final CtConstructorCall<?> call, final Launcher launcher) {
        final List<ServerCandidate> candidates = new ArrayList<>();

        final CtType<?> ownerType = call.getParent(CtType.class);
        if (ownerType != null) {
            for (final CtField<?> field : ownerType.getFields()) {
                if (isServerType(field.getType())) {
                    candidates.add(new ServerCandidate(field.getReference(), field.hasModifier(spoon.reflect.declaration.ModifierKind.STATIC)));
                }
            }
        }

        final CtExecutable<?> executable = call.getParent(CtExecutable.class);
        if (executable != null) {
            for (final CtParameter<?> parameter : executable.getParameters()) {
                if (isServerType(parameter.getType())) {
                    candidates.add(new ServerCandidate(parameter.getReference(), false));
                }
            }

            final CtBlock<?> body = executable.getBody();
            if (body != null) {
                for (final CtLocalVariable<?> localVariable : body.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                    if (isServerType(localVariable.getType())) {
                        candidates.add(new ServerCandidate(localVariable.getReference(), false));
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        final ServerCandidate preferred = candidates.stream()
                .filter(candidate -> "server".equals(candidate.reference.getSimpleName()))
                .findFirst()
                .orElse(candidates.get(0));

        return launcher.getFactory().Code().createVariableRead(preferred.reference, preferred.isStatic);
    }

    private static boolean isServerType(final spoon.reflect.reference.CtTypeReference<?> typeReference) {
        return typeReference != null && SERVER_TYPE.equals(typeReference.getQualifiedName());
    }

    private static String uniqueName(final CtType<?> ownerType, final String base) {
        String candidate = base;
        int index = 1;
        while (ownerType.getField(candidate) != null) {
            candidate = base + index++;
        }
        return candidate;
    }

    private static void normalizePrintedSources(final Path output) {
        try (Stream<Path> paths = Files.walk(output)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::normalizeFile);
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot normalize generated sources", e);
        }
    }

    private static void normalizeFile(final Path file) {
        try {
            final List<String> lines = Files.readAllLines(file);
            final List<String> rewritten = new ArrayList<>();
            for (final String line : lines) {
                if (line.startsWith("import javax.servlet.")) {
                    continue;
                }
                if (line.equals("import org.eclipse.jetty.server.nio.SelectChannelConnector;")) {
                    continue;
                }
                rewritten.add(line);
            }

            final String content = String.join(System.lineSeparator(), rewritten)
                    .replace("new ServerConnector();", "new ServerConnector(server);");
            Files.writeString(file, content + System.lineSeparator());
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot normalize " + file, e);
        }
    }

    private static final class ServerCandidate {
        private final CtVariableReference<?> reference;
        private final boolean isStatic;

        private ServerCandidate(final CtVariableReference<?> reference, final boolean isStatic) {
            this.reference = reference;
            this.isStatic = isStatic;
        }
    }
}
