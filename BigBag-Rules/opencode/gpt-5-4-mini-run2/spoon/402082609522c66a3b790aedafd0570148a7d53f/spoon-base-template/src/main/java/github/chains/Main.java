package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put("org.spongepowered.api.command.CommandSource", "org.spongepowered.api.command.CommandCause");
        TYPE_RENAMES.put("org.spongepowered.api.command.args.CommandContext", "org.spongepowered.api.command.parameter.CommandContext");
        TYPE_RENAMES.put("org.spongepowered.api.command.spec.CommandExecutor", "org.spongepowered.api.command.CommandExecutor");
        TYPE_RENAMES.put("org.spongepowered.api.command.spec.CommandSpec", "org.spongepowered.api.command.Command.Parameterized");
        TYPE_RENAMES.put("org.spongepowered.api.command.CommandException", "org.spongepowered.api.command.exception.CommandException");
        TYPE_RENAMES.put("org.spongepowered.api.text.Text", "net.kyori.adventure.text.Component");
        TYPE_RENAMES.put("org.spongepowered.api.text.serializer.TextSerializers", "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        TYPE_RENAMES.put("org.spongepowered.api.event.network.ClientConnectionEvent.Auth", "org.spongepowered.api.event.network.ServerSideConnectionEvent.Auth");
        TYPE_RENAMES.put("org.spongepowered.api.event.game.state.GamePreInitializationEvent", "org.spongepowered.api.event.lifecycle.ConstructPluginEvent");
        TYPE_RENAMES.put("org.spongepowered.api.event.game.state.GameInitializationEvent", "org.spongepowered.api.event.lifecycle.StartedEngineEvent");
        TYPE_RENAMES.put("org.spongepowered.api.event.game.state.GameStoppingServerEvent", "org.spongepowered.api.event.lifecycle.StoppingEngineEvent");
        TYPE_RENAMES.put("org.spongepowered.api.network.ChannelBinding.RawDataChannel", "org.spongepowered.api.network.channel.raw.RawDataChannel");
        TYPE_RENAMES.put("org.spongepowered.api.network.ChannelRegistrar", "org.spongepowered.api.network.channel.ChannelManager");
        TYPE_RENAMES.put("org.spongepowered.api.network.ChannelBuf", "org.spongepowered.api.network.channel.ChannelBuf");
        TYPE_RENAMES.put("org.spongepowered.api.network.RawDataListener", "org.spongepowered.api.network.channel.raw.play.RawPlayDataHandler");
        TYPE_RENAMES.put("org.spongepowered.api.network.RemoteConnection", "org.spongepowered.api.network.EngineConnection");
    }

    public static void main(String[] args) {
        Path sourceDir = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        rewriteTypes(launcher, factory);
        rewriteInvocations(factory, launcher);
        rewriteMethodSignatures(factory, launcher);

        launcher.prettyprint();
    }

    private static void rewriteTypes(Launcher launcher, Factory factory) {
        List<CtTypeReference<?>> refs = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> ref : refs) {
            String qn = ref.getQualifiedName();
            String replacement = TYPE_RENAMES.get(qn);
            if (replacement != null) {
                ref.replace(factory.Type().createReference(replacement));
            }
        }
    }

    private static void rewriteInvocations(Factory factory, Launcher launcher) {
        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            rewriteInvocation(factory, invocation);
        }
    }

    private static void rewriteInvocation(Factory factory, CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();

        if (isTextOf(invocation)) {
            invocation.setTarget(null);
            invocation.getExecutable().setSimpleName("text");
            return;
        }

        if (isLegacyDeserializer(invocation)) {
            invocation.setTarget(factory.Code().createCodeSnippetExpression("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()"));
            invocation.getExecutable().setSimpleName("deserialize");
            return;
        }

        if (isCommandSpecBuilder(invocation)) {
            invocation.getExecutable().setSimpleName("builder");
            if (invocation.getTarget() != null) {
                invocation.getTarget().setType(factory.Type().createReference("org.spongepowered.api.command.Command"));
            }
            return;
        }

        if (isStringParameterBuilder(invocation)) {
            String literal = extractSingleStringLiteral(invocation.getArguments().get(0));
            if (literal != null) {
                spoon.reflect.code.CtExpression<?> rewritten = factory.Code().createCodeSnippetExpression(
                        "org.spongepowered.api.command.parameter.Parameter.string().key(\"" + escapeJava(literal) + "\").build()");
                invocation.replace(rewritten);
            }
            return;
        }

        if (isFlagBuilder(invocation)) {
            String alias = extractFlagAlias(invocation);
            if (alias != null) {
                spoon.reflect.code.CtExpression<?> rewritten = factory.Code().createCodeSnippetExpression(
                        "org.spongepowered.api.command.parameter.managed.Flag.of(\"" + escapeJava(alias) + "\")");
                invocation.replace(rewritten);
            }
            return;
        }

        if ("arguments".equals(methodName)) {
            rewriteArgumentsChain(factory, invocation);
        }
    }

    private static void rewriteMethodSignatures(Factory factory, Launcher launcher) {
        List<CtMethod<?>> methods = launcher.getModel().getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            if ("handlePayload".equals(method.getSimpleName()) && method.getParameters().size() == 3) {
                if (method.getParameters().get(2).getType() != null
                        && "org.spongepowered.api.Platform.Type".equals(method.getParameters().get(2).getType().getQualifiedName())) {
                    method.getParameters().remove(2);
                }
            }
        }
    }

    private static void rewriteArgumentsChain(Factory factory, CtInvocation<?> argumentsInvocation) {
        List<?> args = argumentsInvocation.getArguments();
        if (args.isEmpty()) {
            return;
        }

        spoon.reflect.code.CtExpression<?> current = null;
        for (Object rawArg : args) {
            if (!(rawArg instanceof spoon.reflect.code.CtExpression)) {
                continue;
            }
            spoon.reflect.code.CtExpression<?> arg = (spoon.reflect.code.CtExpression<?>) rawArg;
            spoon.reflect.code.CtExpression<?> addition;
            if (looksLikeFlag(arg)) {
                addition = factory.Code().createCodeSnippetExpression("__builder__.addFlag(" + arg.toString() + ")");
            } else {
                addition = factory.Code().createCodeSnippetExpression("__builder__.addParameter(" + arg.toString() + ")");
            }

            if (current == null) {
                current = addition;
            } else {
                current = addition;
            }
        }

        if (current != null) {
            argumentsInvocation.replace(current);
            return;
        }
    }

    private static boolean isTextOf(CtInvocation<?> invocation) {
        return "of".equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().size() >= 1
                && (invocation.getTarget() == null || invocation.getTarget().toString().endsWith("Text"));
    }

    private static boolean isLegacyDeserializer(CtInvocation<?> invocation) {
        return "deserialize".equals(invocation.getExecutable().getSimpleName())
                && invocation.getTarget() != null
                && invocation.getTarget().toString().contains("LEGACY_FORMATTING_CODE");
    }

    private static boolean isCommandSpecBuilder(CtInvocation<?> invocation) {
        return "builder".equals(invocation.getExecutable().getSimpleName())
                && invocation.getTarget() != null
                && invocation.getTarget().toString().endsWith("CommandSpec");
    }

    private static boolean isStringParameterBuilder(CtInvocation<?> invocation) {
        return "string".equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().size() == 1
                && invocation.getArguments().get(0).toString().startsWith("of(");
    }

    private static boolean isFlagBuilder(CtInvocation<?> invocation) {
        return "buildWith".equals(invocation.getExecutable().getSimpleName())
                && invocation.getTarget() != null
                && invocation.getTarget().toString().contains("flags()");
    }

    private static boolean looksLikeFlag(spoon.reflect.code.CtExpression<?> expression) {
        return expression.toString().contains("Flag.of") || expression.toString().contains("buildWith") || expression.toString().contains("flags()");
    }

    private static String extractSingleStringLiteral(Object expression) {
        String text = expression.toString();
        if (text.startsWith("of(") && text.endsWith(")")) {
            String inner = text.substring(3, text.length() - 1).trim();
            if (inner.startsWith("\"") && inner.endsWith("\"")) {
                return inner.substring(1, inner.length() - 1);
            }
        }
        return null;
    }

    private static String extractFlagAlias(CtInvocation<?> invocation) {
        String text = invocation.toString();
        int idx = text.indexOf("flag(\"");
        if (idx >= 0) {
            int start = idx + 6;
            int end = text.indexOf('"', start);
            if (end > start) {
                return text.substring(start, end);
            }
        }
        return null;
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
