package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String NEW_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String NEW_HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet.";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet.";

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected input and output directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.buildModel();

        Refactorer refactorer = new Refactorer(launcher);
        refactorer.apply();

        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.prettyprint();
    }

    private static class Refactorer {
        private final Launcher launcher;
        private final Map<CtBlock<?>, String> configNames = new HashMap<>();
        private final Set<CtBlock<?>> insertedConfigs = new HashSet<>();

        Refactorer(Launcher launcher) {
            this.launcher = launcher;
        }

        void apply() {
            renameServletTypes();
            migrateServerSetters();
            migrateConnectorUsage();
            migrateConnectorTypes();
        }

        private void renameServletTypes() {
            List<CtTypeReference<?>> refs = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> ref : refs) {
                String qn = ref.getQualifiedName();
                if (qn != null && qn.startsWith(OLD_SERVLET_PREFIX)) {
                    ref.replace(launcher.getFactory().Type().createReference(
                            NEW_SERVLET_PREFIX + qn.substring(OLD_SERVLET_PREFIX.length())));
                }
            }
        }

        private void migrateServerSetters() {
            List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> invocation : invocations) {
                if (!isJettyServerSetter(invocation)) {
                    continue;
                }

                CtBlock<?> block = enclosingBlock(invocation);
                if (block == null) {
                    continue;
                }

                String configName = ensureHttpConfiguration(block, invocation);
                invocation.setTarget(launcher.getFactory().Code().createCodeSnippetExpression(configName));
            }
        }

        private void migrateConnectorUsage() {
            List<CtConstructorCall<?>> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
            for (CtConstructorCall<?> call : calls) {
                if (!OLD_CONNECTOR.equals(typeName(call.getType()))) {
                    continue;
                }

                CtBlock<?> block = enclosingBlock(call);
                if (block == null) {
                    continue;
                }

                String configName = ensureHttpConfiguration(block, call);
                String serverExpr = findServerExpression(block, call);
                if (serverExpr == null) {
                    continue;
                }

                call.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                call.setArguments(List.of(
                        launcher.getFactory().Code().createCodeSnippetExpression(serverExpr),
                        launcher.getFactory().Code().createCodeSnippetExpression(
                                "new " + NEW_HTTP_CONNECTION_FACTORY + "(" + configName + ")")));
            }
        }

        private void migrateConnectorTypes() {
            List<CtVariable<?>> variables = launcher.getModel().getElements(new TypeFilter<>(CtVariable.class));
            for (CtVariable<?> variable : variables) {
                CtExpression<?> defaultExpression = variable.getDefaultExpression();
                if (!(defaultExpression instanceof CtConstructorCall)) {
                    continue;
                }

                CtConstructorCall<?> call = (CtConstructorCall<?>) defaultExpression;
                if (!OLD_CONNECTOR.equals(typeName(call.getType()))) {
                    continue;
                }

                variable.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            }

            List<CtField<?>> fields = launcher.getModel().getElements(new TypeFilter<>(CtField.class));
            for (CtField<?> field : fields) {
                CtExpression<?> defaultExpression = field.getDefaultExpression();
                if (!(defaultExpression instanceof CtConstructorCall)) {
                    continue;
                }

                CtConstructorCall<?> call = (CtConstructorCall<?>) defaultExpression;
                if (!OLD_CONNECTOR.equals(typeName(call.getType()))) {
                    continue;
                }

                field.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            }
        }

        private boolean isJettyServerSetter(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"setSendServerVersion".equals(methodName) && !"setSendDateHeader".equals(methodName)) {
                return false;
            }

            CtExpression<?> target = invocation.getTarget();
            CtTypeReference<?> targetType = target == null ? null : target.getType();
            return targetType != null && "org.eclipse.jetty.server.Server".equals(targetType.getQualifiedName());
        }

        private CtBlock<?> enclosingBlock(CtStatement statement) {
            CtBlock<?> block = statement.getParent(CtBlock.class);
            return block;
        }

        private String ensureHttpConfiguration(CtBlock<?> block, CtStatement anchor) {
            String existing = configNames.get(block);
            if (existing != null) {
                return existing;
            }

            String baseName = "httpConfiguration";
            String name = baseName;
            int suffix = 1;
            while (hasNameConflict(block, name)) {
                name = baseName + suffix;
                suffix++;
            }

            if (!insertedConfigs.contains(block)) {
                CtStatement declaration = launcher.getFactory().Code().createCodeSnippetStatement(
                        "final " + NEW_HTTP_CONFIGURATION + " " + name + " = new " + NEW_HTTP_CONFIGURATION + "();");
                anchor.insertBefore(declaration);
                insertedConfigs.add(block);
            }

            configNames.put(block, name);
            return name;
        }

        private boolean hasNameConflict(CtBlock<?> block, String name) {
            for (CtVariable<?> variable : block.getElements(new TypeFilter<>(CtVariable.class))) {
                if (name.equals(variable.getSimpleName())) {
                    return true;
                }
            }
            return false;
        }

        private String findServerExpression(CtBlock<?> block, CtStatement anchor) {
            CtInvocation<?> serverInvocation = findRelatedServerInvocation(block);
            if (serverInvocation != null && serverInvocation.getTarget() != null) {
                return serverInvocation.getTarget().toString();
            }

            CtType<?> owner = block.getParent(CtType.class);
            if (owner != null) {
                for (CtField<?> field : owner.getFields()) {
                    CtTypeReference<?> type = field.getType();
                    if (type != null && "org.eclipse.jetty.server.Server".equals(type.getQualifiedName())) {
                        return field.getSimpleName();
                    }
                }
            }

            return null;
        }

        private CtInvocation<?> findRelatedServerInvocation(CtBlock<?> block) {
            for (CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
                CtExpression<?> target = invocation.getTarget();
                if (target == null || target.getType() == null) {
                    continue;
                }
                if ("org.eclipse.jetty.server.Server".equals(target.getType().getQualifiedName())) {
                    String name = invocation.getExecutable().getSimpleName();
                    if (name.startsWith("set") || name.startsWith("add") || name.startsWith("get")) {
                        return invocation;
                    }
                }
            }
            return null;
        }

        private String typeName(CtTypeReference<?> type) {
            return type == null ? null : type.getQualifiedName();
        }
    }
}
