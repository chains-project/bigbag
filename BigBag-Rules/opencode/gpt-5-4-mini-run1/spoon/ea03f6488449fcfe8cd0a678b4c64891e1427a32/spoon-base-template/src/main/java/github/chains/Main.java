package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final Set<String> OLD_PIPELINE_INTERFACES = new HashSet<>(Arrays.asList(
            "redis.clients.jedis.commands.BasicRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
            "redis.clients.jedis.commands.RedisPipeline",
            "redis.clients.jedis.commands.ScriptingCommandsPipeline"
    ));

    private static final Set<String> OLD_BINARY_PIPELINE_INTERFACES = new HashSet<>(Arrays.asList(
            "redis.clients.jedis.commands.BinaryRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
            "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline"
    ));

    private static final String NEW_PIPELINE_INTERFACE = "redis.clients.jedis.commands.PipelineCommands";
    private static final String NEW_BINARY_PIPELINE_INTERFACE = "redis.clients.jedis.commands.PipelineBinaryCommands";
    private static final String CONNECTION_TYPE = "redis.clients.jedis.Connection";
    private static final String CLIENT_TYPE = "redis.clients.jedis.Client";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("expected input source directory");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
        } else {
            launcher.setSourceOutputDirectory(args[0]);
        }
        launcher.buildModel();

        transformTypes(launcher.getModel().getAllTypes());
        launcher.prettyprint();
    }

    private static void transformTypes(Collection<? extends CtType<?>> types) {
        for (CtType<?> type : types) {
            replaceObsoleteInterfaces(type);
            replaceClientImports(type);
            replacePipelineConstructors(type);
            rewriteQueuedCommandDispatch(type);
        }
    }

    private static void replaceObsoleteInterfaces(CtType<?> type) {
        List<CtTypeReference<?>> refs = new ArrayList<>(type.getSuperInterfaces());
        boolean hasPipeline = false;
        boolean hasBinaryPipeline = false;

        for (CtTypeReference<?> ref : refs) {
            String qn = ref.getQualifiedName();
            if (OLD_PIPELINE_INTERFACES.contains(qn)) {
                type.removeSuperInterface(ref);
                hasPipeline = true;
            } else if (OLD_BINARY_PIPELINE_INTERFACES.contains(qn)) {
                type.removeSuperInterface(ref);
                hasBinaryPipeline = true;
            }
        }

        if (hasPipeline) {
            addSuperInterfaceIfMissing(type, NEW_PIPELINE_INTERFACE);
        }
        if (hasBinaryPipeline) {
            addSuperInterfaceIfMissing(type, NEW_BINARY_PIPELINE_INTERFACE);
        }
    }

    private static void addSuperInterfaceIfMissing(CtType<?> type, String qualifiedName) {
        boolean present = type.getSuperInterfaces().stream()
                .anyMatch(r -> qualifiedName.equals(r.getQualifiedName()));
        if (!present) {
            type.addSuperInterface(type.getFactory().createReference(qualifiedName));
        }
    }

    private static void replaceClientImports(CtType<?> type) {
        for (CtTypeReference<?> ref : type.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (CLIENT_TYPE.equals(ref.getQualifiedName())) {
                ref.replace(type.getFactory().createReference(CONNECTION_TYPE));
            }
        }
    }

    private static void replacePipelineConstructors(CtType<?> type) {
        for (CtExecutable<?> executable : type.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null || body.getStatements().isEmpty()) {
                continue;
            }
            CtStatement first = body.getStatements().get(0);
            if (first.toString().contains("super.setClient(")) {
                first.replace(type.getFactory().Code().createCodeSnippetStatement("super(client);"));
            }
        }
    }

    private static void rewriteQueuedCommandDispatch(CtType<?> type) {
        for (CtExecutable<?> executable : type.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null) {
                continue;
            }
            List<CtStatement> statements = new ArrayList<>(body.getStatements());
            for (int i = 0; i < statements.size() - 1; i++) {
                CtStatement current = statements.get(i);
                CtStatement next = statements.get(i + 1);
                if (!current.toString().contains("sendCommand(") && !current.toString().contains("sendBlockingCommand(")) {
                    continue;
                }
                if (!next.toString().contains("getResponse(")) {
                    continue;
                }

                CtInvocation<?> sendInvocation = current.getElements(new TypeFilter<>(CtInvocation.class)).stream().findFirst().orElse(null);
                CtInvocation<?> responseInvocation = next.getElements(new TypeFilter<>(CtInvocation.class)).stream().findFirst().orElse(null);
                if (sendInvocation == null || responseInvocation == null || sendInvocation.getArguments().isEmpty()) {
                    continue;
                }

                String commandExpr = sendInvocation.getArguments().get(0).toString();
                List<String> args = new ArrayList<>();
                for (int a = 1; a < sendInvocation.getArguments().size(); a++) {
                    args.add(sendInvocation.getArguments().get(a).toString());
                }

                boolean blocking = current.toString().contains("sendBlockingCommand(");
                String builderExpr = responseInvocation.getArguments().isEmpty() ? "BuilderFactory.STRING" : responseInvocation.getArguments().get(0).toString();
                StringBuilder replacement = new StringBuilder();
                replacement.append("return appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(")
                        .append(commandExpr)
                        .append(")");
                if (!args.isEmpty()) {
                    replacement.append(".addObjects(").append(String.join(", ", args)).append(")");
                }
                if (blocking) {
                    replacement.append(".blocking()");
                }
                replacement.append(", ").append(builderExpr).append("));");

                current.replace(type.getFactory().Code().createCodeSnippetStatement(replacement.toString()));
                next.delete();
                break;
            }
        }
    }
}
