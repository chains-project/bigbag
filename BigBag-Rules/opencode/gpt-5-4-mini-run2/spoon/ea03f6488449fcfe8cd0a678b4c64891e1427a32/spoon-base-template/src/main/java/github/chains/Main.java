package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    private static final String CONNECTION_FQCN = "redis.clients.jedis.Connection";
    private static final String COMMAND_ARGUMENTS_FQCN = "redis.clients.jedis.CommandArguments";
    private static final String COMMAND_OBJECT_FQCN = "redis.clients.jedis.CommandObject";

    private static final Set<String> LEGACY_PIPELINE_INTERFACES = new HashSet<>(Arrays.asList(
            "redis.clients.jedis.commands.BasicRedisPipeline",
            "redis.clients.jedis.commands.BinaryRedisPipeline",
            "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline",
            "redis.clients.jedis.commands.ClusterPipeline",
            "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
            "redis.clients.jedis.commands.RedisPipeline",
            "redis.clients.jedis.commands.ScriptingCommandsPipeline"
    ));

    private static final List<String> NEW_PIPELINE_INTERFACES = Arrays.asList(
            "redis.clients.jedis.commands.PipelineBinaryCommands",
            "redis.clients.jedis.commands.PipelineCommands",
            "redis.clients.jedis.commands.RedisModulePipelineCommands"
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir] [classpathFile]");
        }

        Path sourceDir = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : sourceDir.resolveSibling(sourceDir.getFileName() + "-spoon-out");
        Path classpathFile = args.length > 2 ? Path.of(args[2]) : sourceDir.resolve("spoon.classpath.tmp");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());

        if (Files.isRegularFile(classpathFile)) {
            launcher.getEnvironment().setSourceClasspath(readClasspath(classpathFile));
        }

        launcher.addInputResource(sourceDir.toString());
        launcher.addProcessor(new LegacyTypeReferenceProcessor());
        launcher.addProcessor(new LegacyTypeDeclarationProcessor());
        launcher.addProcessor(new LegacyConstructorProcessor());
        launcher.addProcessor(new LegacyMemberAccessProcessor());
        launcher.addProcessor(new JedisCommandInvocationProcessor());
        launcher.run();
    }

    private static String[] readClasspath(Path classpathFile) throws IOException {
        String content = Files.readString(classpathFile, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
            return new String[0];
        }
        return content.split(":");
    }

    private static final class LegacyTypeReferenceProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> reference) {
            if (reference == null || reference.getQualifiedName() == null) {
                return;
            }
            if ("redis.clients.jedis.Client".equals(reference.getQualifiedName())) {
                reference.replace(getFactory().Type().createReference(CONNECTION_FQCN));
            }
        }
    }

    private static final class LegacyTypeDeclarationProcessor extends AbstractProcessor<CtType<?>> {
        @Override
        public void process(CtType<?> type) {
            if (type == null || type.getSuperInterfaces().isEmpty()) {
                return;
            }

            boolean changed = false;
            List<CtTypeReference<?>> current = new ArrayList<>(type.getSuperInterfaces());
            for (CtTypeReference<?> ref : current) {
                if (ref != null && LEGACY_PIPELINE_INTERFACES.contains(ref.getQualifiedName())) {
                    type.removeSuperInterface(ref);
                    changed = true;
                }
            }

            if (changed) {
                for (String fqcn : NEW_PIPELINE_INTERFACES) {
                    CtTypeReference<?> newRef = getFactory().Type().createReference(fqcn);
                    if (!type.getSuperInterfaces().contains(newRef)) {
                        type.addSuperInterface(newRef);
                    }
                }
            }
        }
    }

    private static final class LegacyConstructorProcessor extends AbstractProcessor<CtConstructor<?>> {
        @Override
        public void process(CtConstructor<?> constructor) {
            if (constructor == null || constructor.getDeclaringType() == null) {
                return;
            }
            String qualifiedName = constructor.getDeclaringType().getQualifiedName();
            if (!qualifiedName.endsWith("RedisGraphPipeline") && !qualifiedName.endsWith("RedisGraphTransaction")) {
                return;
            }

            constructor.getParameters().forEach(parameter -> {
                if ("redis.clients.jedis.Client".equals(parameter.getType().getQualifiedName())) {
                    parameter.setType(getFactory().Type().createReference(CONNECTION_FQCN));
                }
            });

            if (constructor.getBody() != null && constructor.getBody().toString().contains("super.setClient")) {
                String paramName = constructor.getParameters().isEmpty() ? "client" : constructor.getParameters().get(0).getSimpleName();
                for (CtStatement st : new ArrayList<>(constructor.getBody().getStatements())) {
                    if (st.toString().contains("super.setClient")) {
                        st.replace(getFactory().Code().createCodeSnippetStatement("super(" + paramName + ");"));
                        break;
                    }
                }
            }
        }
    }

    private static final class LegacyMemberAccessProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation == null || invocation.getExecutable() == null) {
                return;
            }
            String name = invocation.getExecutable().getSimpleName();
            if ("getClient".equals(name)) {
                invocation.getExecutable().setSimpleName("getConnection");
            } else if ("getResponse".equals(name)) {
                invocation.getExecutable().setSimpleName("enqueResponse");
            }
        }
    }

    private static final class LegacyFieldAccessProcessor extends AbstractProcessor<CtFieldAccess<?>> {
        @Override
        public void process(CtFieldAccess<?> access) {
            if (access != null && "client".equals(access.getVariable().getSimpleName())) {
                access.replace(getFactory().Code().createCodeSnippetExpression("connection"));
            }
        }
    }

    private static final class LegacyVariableAccessProcessor extends AbstractProcessor<CtVariableAccess<?>> {
        @Override
        public void process(CtVariableAccess<?> access) {
            if (access != null && "client".equals(access.getVariable().getSimpleName())) {
                access.replace(getFactory().Code().createCodeSnippetExpression("connection"));
            }
        }
    }

    private static final class JedisCommandInvocationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation == null || invocation.getExecutable() == null) {
                return;
            }

            String methodName = invocation.getExecutable().getSimpleName();
            if (!"sendCommand".equals(methodName) && !"sendBlockingCommand".equals(methodName) && !"getResponse".equals(methodName)) {
                return;
            }

            if ("getResponse".equals(methodName)) {
                rewriteQueuedCommandPattern(invocation);
                return;
            }

            rewriteDirectCommandInvocation(invocation);
        }

        private void rewriteDirectCommandInvocation(CtInvocation<?> invocation) {
            if (invocation == null || invocation.getTarget() == null || invocation.getArguments().isEmpty()) {
                return;
            }
        }
    }
}
