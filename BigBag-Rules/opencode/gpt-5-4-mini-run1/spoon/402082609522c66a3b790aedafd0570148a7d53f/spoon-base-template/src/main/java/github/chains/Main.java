package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_COMMAND_SOURCE = "org.spongepowered.api.command.CommandSource";
    private static final String OLD_COMMAND_MANAGER = "org.spongepowered.api.command.CommandManager";
    private static final String OLD_COMMAND_CONTEXT = "org.spongepowered.api.command.args.CommandContext";
    private static final String OLD_COMMAND_EXECUTOR = "org.spongepowered.api.command.spec.CommandExecutor";
    private static final String OLD_COMMAND_SPEC = "org.spongepowered.api.command.spec.CommandSpec";
    private static final String OLD_GENERIC_ARGUMENTS = "org.spongepowered.api.command.args.GenericArguments";
    private static final String OLD_COMMAND_EXCEPTION = "org.spongepowered.api.command.CommandException";
    private static final String OLD_TEXT_SERIALIZERS = "org.spongepowered.api.text.serializer.TextSerializers";
    private static final String OLD_TEXT = "org.spongepowered.api.text.Text";
    private static final String OLD_CLIENT_CONNECTION_EVENT = "org.spongepowered.api.event.network.ClientConnectionEvent";
    private static final String OLD_GAME_PRE_INIT = "org.spongepowered.api.event.game.state.GamePreInitializationEvent";
    private static final String OLD_GAME_INIT = "org.spongepowered.api.event.game.state.GameInitializationEvent";
    private static final String OLD_GAME_STOPPING_SERVER = "org.spongepowered.api.event.game.state.GameStoppingServerEvent";
    private static final String OLD_CHANNEL_BINDING_RAW = "org.spongepowered.api.network.ChannelBinding.RawDataChannel";
    private static final String OLD_CHANNEL_REGISTRAR = "org.spongepowered.api.network.ChannelRegistrar";
    private static final String OLD_CHANNEL_BUF = "org.spongepowered.api.network.ChannelBuf";
    private static final String OLD_RAW_DATA_LISTENER = "org.spongepowered.api.network.RawDataListener";
    private static final String OLD_PLUGIN_ANNOTATION = "org.spongepowered.api.plugin.Plugin";

    private static final String NEW_COMMAND_MANAGER = "org.spongepowered.api.command.manager.CommandManager";
    private static final String NEW_COMMAND_SOURCE = "org.spongepowered.api.command.CommandCause";
    private static final String NEW_COMMAND_CONTEXT = "org.spongepowered.api.command.parameter.CommandContext";
    private static final String NEW_COMMAND_EXECUTOR = "org.spongepowered.api.command.CommandExecutor";
    private static final String NEW_COMMAND_SPEC = "org.spongepowered.api.command.Command";
    private static final String NEW_PARAMETER = "org.spongepowered.api.command.parameter.Parameter";
    private static final String NEW_COMMAND_EXCEPTION = "org.spongepowered.api.command.exception.CommandException";
    private static final String NEW_LEGACY_SERIALIZER = "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer";
    private static final String NEW_ADVENTURE_COMPONENT = "net.kyori.adventure.text.Component";
    private static final String NEW_STARTING_ENGINE_EVENT = "org.spongepowered.api.event.lifecycle.StartingEngineEvent";
    private static final String NEW_STARTED_ENGINE_EVENT = "org.spongepowered.api.event.lifecycle.StartedEngineEvent";
    private static final String NEW_STOPPING_ENGINE_EVENT = "org.spongepowered.api.event.lifecycle.StoppingEngineEvent";
    private static final String NEW_REGISTER_COMMAND_EVENT = "org.spongepowered.api.event.lifecycle.RegisterCommandEvent";
    private static final String NEW_REGISTER_CHANNEL_EVENT = "org.spongepowered.api.event.lifecycle.RegisterChannelEvent";
    private static final String NEW_CHANNEL_MANAGER = "org.spongepowered.api.network.channel.ChannelManager";
    private static final String NEW_CHANNEL_BUF = "org.spongepowered.api.network.channel.ChannelBuf";
    private static final String NEW_RAW_DATA_CHANNEL = "org.spongepowered.api.network.channel.raw.RawDataChannel";
    private static final String NEW_PLUGIN_ANNOTATION = "org.spongepowered.plugin.Plugin";
    private static final String NEW_AUDIENCE = "net.kyori.adventure.audience.Audience";
    private static final String NEW_SUBJECT = "org.spongepowered.api.service.permission.Subject";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        rewriteTypeReferences(launcher);
        rewriteExecutables(launcher);
        rewriteInvocations(launcher);
        rewriteAnnotations(launcher);

        launcher.getModel().getAllTypes().forEach(Main::normalizeTypeMembers);
        launcher.prettyprint();
    }

    private static void rewriteTypeReference(Launcher launcher, CtTypeReference<?> ref, String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        String packageName = lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
        String simpleName = lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
        if (!packageName.isEmpty()) {
            CtPackageReference packageReference = launcher.getFactory().Package().createReference(packageName);
            ref.setPackage(packageReference);
        }
        ref.setSimpleName(simpleName);
    }

    private static void rewriteTypeReferences(Launcher launcher) {
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(ref -> {
            switch (ref.getQualifiedName()) {
                case OLD_COMMAND_SOURCE:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_SOURCE);
                    break;
                case OLD_COMMAND_MANAGER:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_MANAGER);
                    break;
                case OLD_COMMAND_CONTEXT:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_CONTEXT);
                    break;
                case OLD_COMMAND_EXECUTOR:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_EXECUTOR);
                    break;
                case OLD_COMMAND_SPEC:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_SPEC);
                    break;
                case OLD_COMMAND_EXCEPTION:
                    rewriteTypeReference(launcher, ref, NEW_COMMAND_EXCEPTION);
                    break;
                case OLD_TEXT_SERIALIZERS:
                    rewriteTypeReference(launcher, ref, NEW_LEGACY_SERIALIZER);
                    break;
                case OLD_TEXT:
                    rewriteTypeReference(launcher, ref, NEW_ADVENTURE_COMPONENT);
                    break;
                case OLD_CLIENT_CONNECTION_EVENT:
                    rewriteTypeReference(launcher, ref, "org.spongepowered.api.event.network.ServerSideConnectionEvent");
                    break;
                case OLD_GAME_PRE_INIT:
                    rewriteTypeReference(launcher, ref, NEW_STARTING_ENGINE_EVENT);
                    break;
                case OLD_GAME_INIT:
                    rewriteTypeReference(launcher, ref, NEW_STARTED_ENGINE_EVENT);
                    break;
                case OLD_GAME_STOPPING_SERVER:
                    rewriteTypeReference(launcher, ref, NEW_STOPPING_ENGINE_EVENT);
                    break;
                case OLD_CHANNEL_BINDING_RAW:
                    rewriteTypeReference(launcher, ref, NEW_RAW_DATA_CHANNEL);
                    break;
                case OLD_CHANNEL_REGISTRAR:
                    rewriteTypeReference(launcher, ref, NEW_CHANNEL_MANAGER);
                    break;
                case OLD_CHANNEL_BUF:
                    rewriteTypeReference(launcher, ref, NEW_CHANNEL_BUF);
                    break;
                case OLD_RAW_DATA_LISTENER:
                    rewriteTypeReference(launcher, ref, "org.spongepowered.api.network.channel.raw.RawDataListener");
                    break;
                default:
                    break;
            }
        });
    }

    private static void rewriteExecutables(Launcher launcher) {
        launcher.getModel().getElements(new TypeFilter<>(CtExecutableReference.class)).forEach(ref -> {
            String qn = ref.getDeclaringType() == null ? null : ref.getDeclaringType().getQualifiedName();
            if (OLD_TEXT_SERIALIZERS.equals(qn) && "deserialize".equals(ref.getSimpleName())) {
                ref.setDeclaringType(launcher.getFactory().createReference(NEW_LEGACY_SERIALIZER));
                ref.setSimpleName("deserialize");
            }
        });
    }

    private static void rewriteInvocations(Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            String owner = executable.getDeclaringType() == null ? null : executable.getDeclaringType().getQualifiedName();
            String name = executable.getSimpleName();

            if (OLD_COMMAND_SPEC.equals(owner) && "builder".equals(name)) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(NEW_COMMAND_SPEC + ".builder()"));
                continue;
            }

            if (OLD_GENERIC_ARGUMENTS.equals(owner)) {
                if ("string".equals(name)) {
                    invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression(NEW_PARAMETER + ".string()"));
                }
                continue;
            }

            if ("org.spongepowered.api.Sponge".equals(owner) && "getCommandManager".equals(name)) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression("org.spongepowered.api.Sponge.commandManager()"));
                continue;
            }

            if ("org.spongepowered.api.Sponge".equals(owner) && "getChannelRegistrar".equals(name)) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression("org.spongepowered.api.Sponge.channelManager()"));
                continue;
            }

            if ("org.spongepowered.api.Sponge".equals(owner) && "getEventManager".equals(name)) {
                invocation.replace(launcher.getFactory().Code().createCodeSnippetExpression("org.spongepowered.api.Sponge.eventManager()"));
            }
        }
    }

    private static void rewriteAnnotations(Launcher launcher) {
        for (CtAnnotation<?> annotation : launcher.getModel().getElements(new TypeFilter<>(CtAnnotation.class))) {
            CtTypeReference<?> type = annotation.getAnnotationType();
            if (type == null) {
                continue;
            }
            if (OLD_PLUGIN_ANNOTATION.equals(type.getQualifiedName())) {
                rewriteTypeReference(launcher, type, NEW_PLUGIN_ANNOTATION);
            }
        }
    }

    private static void normalizeTypeMembers(CtType<?> type) {
        type.getMethods().forEach(method -> {
            if (method.getType() != null && OLD_COMMAND_SOURCE.equals(method.getType().getQualifiedName())) {
                method.setType(method.getFactory().createReference(NEW_COMMAND_SOURCE));
            }
            method.getParameters().forEach(parameter -> {
                String qn = parameter.getType().getQualifiedName();
                if (OLD_COMMAND_SOURCE.equals(qn)) {
                    parameter.setType(method.getFactory().createReference(NEW_COMMAND_SOURCE));
                } else if (OLD_COMMAND_CONTEXT.equals(qn)) {
                    parameter.setType(method.getFactory().createReference(NEW_COMMAND_CONTEXT));
                }
            });
        });
    }
}
