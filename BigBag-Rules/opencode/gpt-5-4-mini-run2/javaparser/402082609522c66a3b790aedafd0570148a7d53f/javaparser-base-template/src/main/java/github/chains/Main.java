package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteMethodDeclarations(cu);
            changed |= rewriteMethodCalls(cu);
            changed |= rewriteFieldAccesses(cu);

            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to rewrite " + file, ex);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceImport(cu, "org.spongepowered.api.command.CommandSource", "org.spongepowered.api.command.CommandCause");
        changed |= replaceImport(cu, "org.spongepowered.api.command.args.CommandContext", "org.spongepowered.api.command.parameter.CommandContext");
        changed |= replaceImport(cu, "org.spongepowered.api.command.args.GenericArguments", "org.spongepowered.api.command.parameter.Parameter");
        changed |= replaceImport(cu, "org.spongepowered.api.command.spec.CommandExecutor", "org.spongepowered.api.command.CommandExecutor");
        changed |= replaceImport(cu, "org.spongepowered.api.command.spec.CommandSpec", "org.spongepowered.api.command.Command");
        changed |= replaceImport(cu, "org.spongepowered.api.command.CommandManager", "org.spongepowered.api.command.manager.CommandManager");
        changed |= replaceImport(cu, "org.spongepowered.api.network.ChannelRegistrar", "org.spongepowered.api.network.channel.ChannelManager");
        changed |= replaceImport(cu, "org.spongepowered.api.network.ChannelBuf", "org.spongepowered.api.network.channel.ChannelBuf");
        changed |= replaceImport(cu, "org.spongepowered.api.network.RawDataListener", "org.spongepowered.api.network.channel.raw.play.RawPlayDataHandler");
        changed |= replaceImport(cu, "org.spongepowered.api.network.ChannelBinding.RawDataChannel", "org.spongepowered.api.network.channel.raw.RawDataChannel");
        changed |= replaceImport(cu, "org.spongepowered.api.text.Text", "net.kyori.adventure.text.Component");
        changed |= replaceImport(cu, "org.spongepowered.api.text.serializer.TextSerializers", "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        changed |= replaceImport(cu, "org.spongepowered.api.event.game.state.GameInitializationEvent", "org.spongepowered.api.event.lifecycle.StartedEngineEvent");
        changed |= replaceImport(cu, "org.spongepowered.api.event.game.state.GamePreInitializationEvent", "org.spongepowered.api.event.lifecycle.LoadedGameEvent");
        changed |= replaceImport(cu, "org.spongepowered.api.event.game.state.GameStoppingServerEvent", "org.spongepowered.api.event.lifecycle.StoppingEngineEvent");
        return changed;
    }

    private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
        boolean changed = false;
        for (ImportDeclaration imp : new ArrayList<>(cu.getImports())) {
            if (imp.getNameAsString().equals(oldName)) {
                imp.setName(newName);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodDeclarations(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getType().asString().equals("CommandSpec")) {
                method.setType("Command.Parameterized");
                changed = true;
            }
            if (method.getNameAsString().equals("execute") && method.getParameters().size() == 2
                    && method.getParameters().get(0).getType().asString().equals("CommandSource")
                    && method.getParameters().get(1).getType().asString().equals("CommandContext")) {
                method.getParameter(0).setType("CommandCause");
                method.getParameter(1).setType("org.spongepowered.api.command.parameter.CommandContext");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getScope().isPresent() && call.getScope().get() instanceof MethodCallExpr) {
                MethodCallExpr scope = (MethodCallExpr) call.getScope().get();
                if (isSpongeGetServer(scope)) {
                    if (call.getNameAsString().equals("getGameProfileManager")) {
                        call.setName("gameProfileManager");
                        changed = true;
                    } else if (call.getNameAsString().equals("getPlayer")) {
                        call.setName("player");
                        changed = true;
                    } else if (call.getNameAsString().equals("getWorlds")) {
                        call.setScope(parseExpr("Sponge.server().worldManager()"));
                        call.setName("worlds");
                        changed = true;
                    }
                }
            }

            if (call.getScope().isPresent() && call.getScope().get().isNameExpr()) {
                NameExpr scope = call.getScope().get().asNameExpr();
                if (scope.getNameAsString().equals("Sponge")) {
                    switch (call.getNameAsString()) {
                        case "getEventManager":
                            call.setName("eventManager");
                            changed = true;
                            break;
                        case "getCommandManager":
                            call.setScope(parseExpr("Sponge.server()"));
                            call.setName("commandManager");
                            changed = true;
                            break;
                        case "getChannelRegistrar":
                            call.setName("channelManager");
                            changed = true;
                            break;
                        case "getServer":
                            call.setName("server");
                            changed = true;
                            break;
                        default:
                            break;
                    }
                }
            }

            if (call.getNameAsString().equals("getOne") && call.getArguments().size() == 1
                    && call.getArguments().get(0).isStringLiteralExpr()) {
                String key = call.getArgument(0).asStringLiteralExpr().asString();
                call.setName("requireOne");
                call.setArguments(NodeList.nodeList(parseExpr("org.spongepowered.api.command.parameter.Parameter.key(\"" + escape(key) + "\", String.class)")));
                changed = true;
            }

            if (call.getNameAsString().equals("hasAny") && call.getArguments().size() == 1
                    && call.getArguments().get(0).isStringLiteralExpr()) {
                call.setName("hasFlag");
                changed = true;
            }

            if (call.getNameAsString().equals("builder") && call.getScope().isPresent()
                    && call.getScope().get().isNameExpr() && call.getScope().get().asNameExpr().getNameAsString().equals("CommandSpec")) {
                call.getScope().get().asNameExpr().setName("Command");
                changed = true;
            }

            if (call.getNameAsString().equals("string") && call.getScope().isPresent()
                    && call.getScope().get().isNameExpr() && call.getScope().get().asNameExpr().getNameAsString().equals("GenericArguments")) {
                call.setScope(parseExpr("org.spongepowered.api.command.parameter.Parameter"));
                call.setName("string");
                changed = true;
            }

            if (call.getNameAsString().equals("flags") && call.getScope().isEmpty()) {
                changed = true;
            }

            if (isTextDeserialize(call)) {
                call.setScope(parseExpr("LegacyComponentSerializer.legacySection()"));
                call.setName("deserialize");
                changed = true;
            }

            if (isTextOf(call)) {
                call.setScope(parseExpr("Component"));
                call.setName("text");
                changed = true;
            }

            if (call.getNameAsString().equals("createProfileProperty") && call.getArguments().size() == 3) {
                call.setScope(parseExpr("org.spongepowered.api.profile.property.ProfileProperty"));
                call.setName("of");
                changed = true;
            }

            if (call.getNameAsString().equals("clear") && isGetPropertyMap(call.getScope().orElse(null))) {
                call.replace(parseExpr(propertyTarget(call.getScope().get()) + " = " + propertyTarget(call.getScope().get()) + ".withoutProperties()"));
                changed = true;
            }

            if (call.getNameAsString().equals("put") && isGetPropertyMap(call.getScope().orElse(null)) && call.getArguments().size() == 2) {
                Expression value = call.getArgument(1);
                call.replace(parseExpr(propertyTarget(call.getScope().get()) + " = " + propertyTarget(call.getScope().get()) + ".withProperty(" + value + ")"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteFieldAccesses(CompilationUnit cu) {
        boolean changed = false;
        for (FieldAccessExpr field : cu.findAll(FieldAccessExpr.class)) {
            if (field.getScope().isNameExpr() && field.getScope().asNameExpr().getNameAsString().equals("TextSerializers")
                    && field.getNameAsString().equals("LEGACY_FORMATTING_CODE")) {
                field.replace(parseExpr("LegacyComponentSerializer.legacySection()"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isSpongeGetServer(MethodCallExpr call) {
        return call.getNameAsString().equals("getServer")
                && call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && call.getScope().get().asNameExpr().getNameAsString().equals("Sponge");
    }

    private static boolean isTextDeserialize(MethodCallExpr call) {
        return call.getNameAsString().equals("deserialize")
                && call.getScope().isPresent()
                && call.getScope().get().isFieldAccessExpr()
                && call.getScope().get().asFieldAccessExpr().getScope().isNameExpr()
                && call.getScope().get().asFieldAccessExpr().getScope().asNameExpr().getNameAsString().equals("TextSerializers");
    }

    private static boolean isTextOf(MethodCallExpr call) {
        return call.getNameAsString().equals("of") && call.getScope().isEmpty();
    }

    private static boolean isGetPropertyMap(Expression expression) {
        return expression instanceof MethodCallExpr && ((MethodCallExpr) expression).getNameAsString().equals("getPropertyMap");
    }

    private static String propertyTarget(Expression expression) {
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            return call.getScope().map(Expression::toString).orElse("profile");
        }
        return expression != null ? expression.toString() : "profile";
    }

    private static Expression parseExpr(String code) {
        return new JavaParser().parseExpression(code)
                .getResult()
                .orElseThrow(() -> new IllegalArgumentException("Unable to parse expression: " + code));
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
