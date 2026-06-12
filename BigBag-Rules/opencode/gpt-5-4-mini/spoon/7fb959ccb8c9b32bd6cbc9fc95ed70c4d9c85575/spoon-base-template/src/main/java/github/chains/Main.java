package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1
                ? Paths.get(args[1])
                : input.getParent().resolve(input.getFileName().toString() + "-spoon-out");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();

        transformServletTypes(launcher);
        transformJetty11Breakage(launcher);

        launcher.prettyprint();
        cleanupGeneratedSources(output);
    }

    private static void transformServletTypes(Launcher launcher) {
        final List<CtTypeReference<?>> references = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : references) {
            final String qualifiedName = reference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(launcher.getFactory().Type().createReference(
                        NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }
    }

    private static void transformJetty11Breakage(Launcher launcher) {
        for (CtExecutable<?> executable : launcher.getModel().getElements(new TypeFilter<>(CtExecutable.class))) {
            if (executable.getBody() == null) {
                continue;
            }

            final List<CtInvocation<?>> invocations = executable.getBody().getElements(new TypeFilter<>(CtInvocation.class));
            final List<CtConstructorCall<?>> constructorCalls = executable.getBody().getElements(new TypeFilter<>(CtConstructorCall.class));

            String configName = null;
            for (CtInvocation<?> invocation : invocations) {
                if (isLegacyServerConfigCall(invocation)) {
                    if (configName == null) {
                        configName = uniqueName(executable, "jettyHttpConfiguration");
                        insertConfigurationDeclaration(executable, configName, launcher);
                    }
                    invocation.setTarget(launcher.getFactory().Code().createCodeSnippetExpression(configName));
                }
            }

            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                if (!OLD_CONNECTOR.equals(constructorCall.getType().getQualifiedName())) {
                    continue;
                }

                final String serverExpr = findServerExpression(executable);
                if (serverExpr == null) {
                    continue;
                }

                updateConnectorDeclaration(constructorCall, launcher);
                final String replacement = configName != null
                        ? "new " + NEW_CONNECTOR + "(" + serverExpr + ", new " + HTTP_CONNECTION_FACTORY + "(" + configName + "))"
                        : "new " + NEW_CONNECTOR + "(" + serverExpr + ")";
                constructorCall.replace(launcher.getFactory().Code().createCodeSnippetExpression(replacement));
            }
        }
    }

    private static boolean isLegacyServerConfigCall(CtInvocation<?> invocation) {
        final String methodName = invocation.getExecutable().getSimpleName();
        if (!"setSendServerVersion".equals(methodName) && !"setSendDateHeader".equals(methodName)) {
            return false;
        }
        final CtExpression<?> target = invocation.getTarget();
        return target != null && target.getType() != null && SERVER_TYPE.equals(target.getType().getQualifiedName());
    }

    private static void insertConfigurationDeclaration(CtExecutable<?> executable, String configName, Launcher launcher) {
        final String declaration = "final " + HTTP_CONFIGURATION + " " + configName + " = new " + HTTP_CONFIGURATION + "()";
        final CtStatement configStatement = launcher.getFactory().Code().createCodeSnippetStatement(declaration);
        if (!executable.getBody().getStatements().isEmpty()) {
            final CtStatement first = executable.getBody().getStatements().get(0);
            first.insertAfter(configStatement);
        } else {
            executable.getBody().addStatement(configStatement);
        }
    }

    private static void cleanupGeneratedSources(Path output) {
        try (Stream<Path> paths = Files.walk(output)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::cleanupJavaFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanupJavaFile(Path file) {
        try {
            final List<String> lines = Files.readAllLines(file);
            final StringBuilder rewritten = new StringBuilder();
            for (String line : lines) {
                final String trimmed = line.trim();
                if (trimmed.startsWith("import javax.servlet.")) {
                    continue;
                }
                if (trimmed.equals("import org.eclipse.jetty.server.nio.SelectChannelConnector;")) {
                    continue;
                }
                rewritten.append(line).append(System.lineSeparator());
            }
            Files.writeString(file, rewritten.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String findServerExpression(CtExecutable<?> executable) {
        final List<CtExpression<?>> expressions = executable.getBody().getElements(new TypeFilter<>(CtExpression.class));
        for (CtExpression<?> expression : expressions) {
            final CtTypeReference<?> type = expression.getType();
            if (type != null && SERVER_TYPE.equals(type.getQualifiedName())) {
                return expression.toString();
            }
        }
        return null;
    }

    private static void updateConnectorDeclaration(CtConstructorCall<?> constructorCall, Launcher launcher) {
        final CtElement parent = constructorCall.getParent();
        if (parent instanceof CtLocalVariable) {
            ((CtLocalVariable<?>) parent).setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            return;
        }

        if (parent instanceof CtField) {
            ((CtField<?>) parent).setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            return;
        }

        if (parent instanceof CtAssignment) {
            final CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
            final CtExpression<?> assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableRead) {
                final CtVariableRead<?> variableRead = (CtVariableRead<?>) assigned;
                if (variableRead.getVariable() != null && variableRead.getVariable().getDeclaration() != null) {
                    variableRead.getVariable().getDeclaration().setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                }
            }
        }
    }

    private static String uniqueName(CtExecutable<?> executable, String baseName) {
        final Set<String> names = new HashSet<>();
        final CtElement scope = executable.getBody();
        for (CtLocalVariable<?> localVariable : scope.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            names.add(localVariable.getSimpleName());
        }
        if (executable.getParent() != null) {
            for (Object fieldObj : executable.getParent(CtType.class).getFields()) {
                CtField<?> field = (CtField<?>) fieldObj;
                names.add(field.getSimpleName());
            }
        }

        String candidate = baseName;
        int index = 1;
        while (names.contains(candidate)) {
            candidate = baseName + index++;
        }
        return candidate;
    }
}
