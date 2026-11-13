package github.chains;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_CLIENT = "redis.clients.jedis.Client";
    private static final String NEW_CONNECTION = "redis.clients.jedis.Connection";

    private static final Set<String> REMOVED_PIPELINE_INTERFACES = new HashSet<>(Arrays.asList(
            "redis.clients.jedis.commands.BasicRedisPipeline",
            "redis.clients.jedis.commands.BinaryRedisPipeline",
            "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline",
            "redis.clients.jedis.commands.ClusterPipeline",
            "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
            "redis.clients.jedis.commands.RedisPipeline",
            "redis.clients.jedis.commands.ScriptingCommandsPipeline"));

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new JedisCompatibilityProcessor());
        launcher.run();
    }

    private static class JedisCompatibilityProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            rewriteTypeReferences(element);
            rewriteRemovedPipelineInterfaces(element);
            rewriteLegacyResponseEnqueueCalls(element);
            rewriteLegacyPipelineConstructors(element);
        }

        private void rewriteTypeReferences(CtElement element) {
            if (!(element instanceof CtTypeReference)) {
                return;
            }

            CtTypeReference<?> reference = (CtTypeReference<?>) element;
            if (OLD_CLIENT.equals(reference.getQualifiedName())) {
                reference.replace(reference.getFactory().Type().createReference(NEW_CONNECTION));
            }
        }

        private void rewriteRemovedPipelineInterfaces(CtElement element) {
            if (!(element instanceof CtType)) {
                return;
            }

            CtType<?> type = (CtType<?>) element;
            java.util.Set<CtTypeReference<?>> kept = new java.util.HashSet<>();
            for (CtTypeReference<?> ref : type.getSuperInterfaces()) {
                if (!REMOVED_PIPELINE_INTERFACES.contains(ref.getQualifiedName())) {
                    kept.add(ref);
                }
            }
            if (kept.size() != type.getSuperInterfaces().size()) {
                type.setSuperInterfaces(kept);
                if (type.isInterface()) {
                    ensureInterface(type, "redis.clients.jedis.commands.PipelineCommands");
                    ensureInterface(type, "redis.clients.jedis.commands.PipelineBinaryCommands");
                    ensureInterface(type, "redis.clients.jedis.commands.RedisModulePipelineCommands");
                }
            }
        }

        private void rewriteLegacyResponseEnqueueCalls(CtElement element) {
            if (!(element instanceof CtInvocation)) {
                return;
            }

            CtInvocation<?> invocation = (CtInvocation<?>) element;
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable != null && "getResponse".equals(executable.getSimpleName())) {
                executable.setSimpleName("enqueResponse");
            }

            if (invocation.getTarget() instanceof CtFieldAccess) {
                CtFieldAccess<?> access = (CtFieldAccess<?>) invocation.getTarget();
                if ("client".equals(access.getVariable().getSimpleName())) {
                    access.replace(invocation.getFactory().Code().createCodeSnippetExpression("connection"));
                }
            }
        }

        private void rewriteLegacyPipelineConstructors(CtElement element) {
            if (!(element instanceof CtConstructor)) {
                return;
            }

            CtConstructor<?> constructor = (CtConstructor<?>) element;
            CtTypeReference<?> declaringType = constructor.getDeclaringType() != null
                    ? constructor.getDeclaringType().getSuperclass()
                    : null;
            if (declaringType == null) {
                return;
            }

            String superClass = declaringType.getQualifiedName();
            if (!"redis.clients.jedis.Pipeline".equals(superClass) && !"redis.clients.jedis.Transaction".equals(superClass)) {
                return;
            }

            if (constructor.getBody().getStatements().isEmpty()) {
                return;
            }

            CtStatement first = constructor.getBody().getStatements().get(0);
            if (!(first instanceof CtInvocation)) {
                return;
            }

            CtInvocation<?> invocation = (CtInvocation<?>) first;
            if (invocation.getExecutable() != null
                    && "setClient".equals(invocation.getExecutable().getSimpleName())
                    && !invocation.getArguments().isEmpty()) {
                CtStatement replacement = constructor.getFactory().Code().createCodeSnippetStatement(
                        "super(" + invocation.getArguments().get(0) + ");");
                first.replace(replacement);
            }
        }

        private void ensureInterface(CtType<?> type, String qualifiedName) {
            boolean exists = type.getSuperInterfaces().stream()
                    .anyMatch(ref -> qualifiedName.equals(ref.getQualifiedName()));
            if (!exists) {
                type.addSuperInterface(type.getFactory().Type().createReference(qualifiedName));
            }
        }
    }
}
