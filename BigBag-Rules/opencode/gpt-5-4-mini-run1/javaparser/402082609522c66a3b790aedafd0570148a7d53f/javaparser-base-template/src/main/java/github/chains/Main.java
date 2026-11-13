package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class Main {
    private static final JavaParser PARSER = new JavaParser(new ParserConfiguration());
    private static final Map<String, String> IMPORT_REWRITES = new LinkedHashMap<>();

    static {
        IMPORT_REWRITES.put("org.spongepowered.api.command.CommandException", "org.spongepowered.api.command.exception.CommandException");
        IMPORT_REWRITES.put("org.spongepowered.api.command.CommandManager", "org.spongepowered.api.command.manager.CommandManager");
        IMPORT_REWRITES.put("org.spongepowered.api.command.CommandSource", "org.spongepowered.api.command.CommandCause");
        IMPORT_REWRITES.put("org.spongepowered.api.command.args.CommandContext", "org.spongepowered.api.command.parameter.CommandContext");
        IMPORT_REWRITES.put("org.spongepowered.api.command.args.GenericArguments", "org.spongepowered.api.command.parameter.Parameter");
        IMPORT_REWRITES.put("org.spongepowered.api.command.spec.CommandExecutor", "org.spongepowered.api.command.CommandExecutor");
        IMPORT_REWRITES.put("org.spongepowered.api.command.spec.CommandSpec", "org.spongepowered.api.command.Command");
        IMPORT_REWRITES.put("org.spongepowered.api.event.game.state.GameInitializationEvent", "org.spongepowered.api.event.lifecycle.LoadedGameEvent");
        IMPORT_REWRITES.put("org.spongepowered.api.event.game.state.GamePreInitializationEvent", "org.spongepowered.api.event.lifecycle.ConstructPluginEvent");
        IMPORT_REWRITES.put("org.spongepowered.api.event.game.state.GameStoppingServerEvent", "org.spongepowered.api.event.lifecycle.StoppedGameEvent");
        IMPORT_REWRITES.put("org.spongepowered.api.event.network.ClientConnectionEvent", "org.spongepowered.api.event.network.ServerSideConnectionEvent");
        IMPORT_REWRITES.put("org.spongepowered.api.network.ChannelBinding.RawDataChannel", "org.spongepowered.api.network.channel.raw.RawDataChannel");
        IMPORT_REWRITES.put("org.spongepowered.api.network.ChannelBuf", "org.spongepowered.api.network.channel.ChannelBuf");
        IMPORT_REWRITES.put("org.spongepowered.api.network.ChannelRegistrar", "org.spongepowered.api.network.channel.ChannelManager");
        IMPORT_REWRITES.put("org.spongepowered.api.network.RawDataListener", "org.spongepowered.api.network.channel.raw.play.RawPlayDataHandler");
        IMPORT_REWRITES.put("org.spongepowered.api.network.RemoteConnection", "org.spongepowered.api.network.EngineConnection");
        IMPORT_REWRITES.put("org.spongepowered.api.text.Text", "net.kyori.adventure.text.Component");
        IMPORT_REWRITES.put("org.spongepowered.api.text.serializer.TextSerializers", "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        IMPORT_REWRITES.put("org.spongepowered.api.data.key.Keys", "org.spongepowered.api.data.Keys");
        IMPORT_REWRITES.put("com.flowpowered.math.vector.Vector3d", "org.spongepowered.math.vector.Vector3d");
        IMPORT_REWRITES.put("org.spongepowered.api.command.args.GenericArguments.string", "org.spongepowered.api.command.parameter.Parameter.string");
        IMPORT_REWRITES.put("org.spongepowered.api.command.args.GenericArguments.flags", "org.spongepowered.api.command.parameter.managed.Flag.of");
        IMPORT_REWRITES.put("org.spongepowered.api.command.args.GenericArguments.none", "org.spongepowered.api.command.parameter.Parameter.bool");
        IMPORT_REWRITES.put("org.spongepowered.api.text.Text.of", "net.kyori.adventure.text.Component.text");
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path file) {
        try {
            Optional<CompilationUnit> parsed = PARSER.parse(file).getResult();
            if (parsed.isEmpty()) {
                return;
            }

            CompilationUnit cu = parsed.get();
            boolean changed = false;

            changed |= rewriteImports(cu);
            changed |= rewriteTypeNames(cu);
            changed |= rewriteSpongeAccessors(cu);
            changed |= rewriteTextSerialization(cu);
            changed |= rewriteCommandContexts(cu);
            changed |= rewriteCommandBuilders(cu);
            changed |= rewriteCommandExecutorSignatures(cu);
            changed |= rewriteLifecycleMethods(cu);
            changed |= rewriteNetworkListeners(cu);

            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to transform " + file, ex);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        List<String> toAdd = new ArrayList<>();
        List<String> existing = new ArrayList<>();

        for (int i = 0; i < cu.getImports().size(); i++) {
            var imp = cu.getImports().get(i);
            String name = imp.getNameAsString();
            if (IMPORT_REWRITES.containsKey(name)) {
                toAdd.add(IMPORT_REWRITES.get(name));
                cu.getImports().remove(i--);
                changed = true;
                continue;
            }

            existing.add(name);
        }

        for (String add : toAdd) {
            if (!existing.contains(add)) {
                cu.addImport(add);
            }
        }

        if (cu.getImports().stream().anyMatch(i -> i.isAsterisk() && i.getNameAsString().equals("org.spongepowered.api.command.args"))) {
            cu.getImports().removeIf(i -> i.isAsterisk() && i.getNameAsString().equals("org.spongepowered.api.command.args"));
            cu.addImport("org.spongepowered.api.command.parameter.Parameter");
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteTypeNames(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceType(cu, "CommandSpec", "Command.Parameterized");
        changed |= replaceType(cu, "CommandExecutor", "CommandExecutor");
        changed |= replaceType(cu, "CommandContext", "CommandContext");
        changed |= replaceType(cu, "CommandSource", "CommandCause");
        changed |= replaceType(cu, "Text", "Component");
        changed |= replaceType(cu, "TextSerializers", "LegacyComponentSerializer");
        changed |= replaceType(cu, "ChannelRegistrar", "ChannelManager");
        changed |= replaceType(cu, "RawDataListener", "RawPlayDataHandler");
        changed |= replaceType(cu, "RawDataChannel", "RawDataChannel");
        changed |= replaceType(cu, "ChannelBuf", "ChannelBuf");
        changed |= replaceType(cu, "RemoteConnection", "EngineConnection");
        changed |= replaceType(cu, "GamePreInitializationEvent", "ConstructPluginEvent");
        changed |= replaceType(cu, "GameInitializationEvent", "LoadedGameEvent");
        changed |= replaceType(cu, "GameStoppingServerEvent", "StoppedGameEvent");
        changed |= replaceType(cu, "ClientConnectionEvent", "ServerSideConnectionEvent");
        changed |= replaceType(cu, "Plugin", "Plugin");
        changed |= replaceType(cu, "Keys", "Keys");
        changed |= replaceType(cu, "Vector3d", "Vector3d");
        changed |= replaceType(cu, "GenericArguments", "Parameter");
        return changed;
    }

    private static boolean replaceType(CompilationUnit cu, String oldSimpleName, String newType) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals(oldSimpleName)) {
                type.replace(com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType(newType));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteSpongeAccessors(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty()) {
                continue;
            }

            Optional<Expression> scope = call.getScope();
            if (scope.get().isNameExpr() && scope.get().asNameExpr().getNameAsString().equals("Sponge")) {
                String name = call.getNameAsString();
                switch (name) {
                    case "getServer":
                        call.setName("server");
                        changed = true;
                        break;
                    case "getEventManager":
                        call.setName("eventManager");
                        changed = true;
                        break;
                    case "getCommandManager":
                        call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Sponge.server().commandManager()"));
                        changed = true;
                        break;
                    case "getChannelRegistrar":
                        call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Sponge.channelManager()"));
                        changed = true;
                        break;
                    case "getPluginManager":
                        call.setName("pluginManager");
                        changed = true;
                        break;
                    default:
                        break;
                }
            }

            String name = call.getNameAsString();
            if (name.equals("getWorlds") && scope.get().toString().contains("Sponge.server()")) {
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Sponge.server().worldManager().worlds()"));
                changed = true;
            } else if (name.equals("getGameProfileManager") && scope.get().toString().contains("Sponge.server()")) {
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Sponge.server().gameProfileManager()"));
                changed = true;
            } else if (name.equals("getPlayer") && scope.get().toString().contains("Sponge.server()")) {
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Sponge.server().player(" + call.getArguments().get(0) + ")"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteTextSerialization(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("deserialize") && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                String scopeText = scope.toString();
                if (scopeText.contains("TextSerializers.LEGACY_FORMATTING_CODE")) {
                    call.replace(com.github.javaparser.StaticJavaParser.parseExpression("LegacyComponentSerializer.legacySection().deserialize(" + call.getArgument(0) + ")"));
                    changed = true;
                }
            }

            if (call.getScope().isPresent() && call.getScope().get().toString().equals("Text") && call.getNameAsString().equals("of")) {
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("Component.text(" + joinArgs(call.getArguments()) + ")"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteCommandContexts(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("getOne") && call.getArguments().size() == 1) {
                Expression arg = call.getArgument(0);
                String key = arg.toString().replace("\"", "");
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("args.one(Parameter.key(\"" + key + "\", String.class))"));
                changed = true;
            } else if (call.getNameAsString().equals("hasAny") && call.getArguments().size() == 1) {
                call.replace(com.github.javaparser.StaticJavaParser.parseExpression("args.hasFlag(" + call.getArgument(0) + ")"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteCommandBuilders(CompilationUnit cu) {
        boolean changed = false;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getScope().isPresent() && call.getScope().get().toString().equals("CommandSpec") && call.getNameAsString().equals("builder")) {
                call.getScope().get().replace(com.github.javaparser.StaticJavaParser.parseExpression("Command"));
                call.setName("builder");
                changed = true;
            }

            if (call.getNameAsString().equals("arguments")) {
                call.setName("addParameters");
                changed = true;
            }

            if (call.getNameAsString().equals("buildWith")) {
                call.setName("build");
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals("CommandSpec")) {
                type.replace(com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType("Command.Parameterized"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteCommandExecutorSignatures(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!method.getNameAsString().equals("execute")) {
                continue;
            }
            if (method.getParameters().size() == 2
                    && method.getParameters().get(0).getType().toString().contains("CommandSource")
                    && method.getParameters().get(1).getType().toString().contains("CommandContext")) {
                method.getParameter(0).setType("CommandCause");
                method.getParameter(1).setType("CommandContext");
                method.getBody().ifPresent(body -> {
                    if (body.stream().noneMatch(stmt -> stmt.toString().contains("args.cause()"))) {
                        body.addStatement(0, com.github.javaparser.StaticJavaParser.parseStatement("CommandCause src = args.cause();"));
                    }
                });
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteLifecycleMethods(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getParameters().size() != 1) {
                continue;
            }
            String type = method.getParameter(0).getType().toString();
            if (type.endsWith("GamePreInitializationEvent")) {
                method.getParameter(0).setType("ConstructPluginEvent");
                changed = true;
            } else if (type.endsWith("GameInitializationEvent")) {
                method.getParameter(0).setType("LoadedGameEvent");
                changed = true;
            } else if (type.endsWith("GameStoppingServerEvent")) {
                method.getParameter(0).setType("StoppedGameEvent");
                changed = true;
            } else if (type.endsWith("ClientConnectionEvent.Auth")) {
                method.getParameter(0).setType("ServerSideConnectionEvent.Login");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteNetworkListeners(CompilationUnit cu) {
        boolean changed = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getParameters().size() == 3 && method.getNameAsString().equals("handlePayload")) {
                method.getParameter(1).setType("EngineConnection");
                method.getParameters().remove(2);
                changed = true;
            }

            if (method.getNameAsString().equals("handleRequest") && method.getParameters().size() == 3) {
                method.getParameter(1).setType("EngineConnection");
                changed = true;
            }
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("addListener") && call.getArguments().size() == 2) {
                call.setName("addHandler");
                call.getArguments().remove(0);
                changed = true;
            }

            if (call.getNameAsString().equals("getOrCreateRaw")) {
                call.setName("ofType");
                changed = true;
            }
        }

        return changed;
    }

    private static String joinArgs(NodeList<Expression> expressions) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expressions.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(expressions.get(i));
        }
        return builder.toString();
    }
}
