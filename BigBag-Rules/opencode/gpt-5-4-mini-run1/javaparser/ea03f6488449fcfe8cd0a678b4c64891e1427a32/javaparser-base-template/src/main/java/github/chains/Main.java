package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    private static final Set<String> OLD_JEDIS_MARKERS = Set.of(
            "redis.clients.jedis.Client",
            "Client",
            "redis.clients.jedis.commands.BasicRedisPipeline",
            "BasicRedisPipeline",
            "redis.clients.jedis.commands.BinaryRedisPipeline",
            "BinaryRedisPipeline",
            "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline",
            "BinaryScriptingCommandsPipeline",
            "redis.clients.jedis.commands.ClusterPipeline",
            "ClusterPipeline",
            "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
            "MultiKeyBinaryRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
            "MultiKeyCommandsPipeline",
            "redis.clients.jedis.commands.RedisPipeline",
            "RedisPipeline",
            "redis.clients.jedis.commands.ScriptingCommandsPipeline"
            ,"ScriptingCommandsPipeline"
    );

    private static final List<String> PIPELINE_INTERFACES = List.of(
            "redis.clients.jedis.commands.DatabasePipelineCommands",
            "redis.clients.jedis.commands.PipelineBinaryCommands",
            "redis.clients.jedis.commands.PipelineCommands",
            "redis.clients.jedis.commands.RedisModulePipelineCommands"
    );

    private static final List<String> TRANSACTION_INTERFACES = List.of(
            "redis.clients.jedis.commands.PipelineBinaryCommands",
            "redis.clients.jedis.commands.PipelineCommands",
            "redis.clients.jedis.commands.RedisModulePipelineCommands"
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transformFile(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to transform " + path, e);
                    }
                });
    }

    private static void transformFile(Path path) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;

        if (rewriteImports(cu)) {
            changed = true;
        }

        for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (rewriteJedisInheritance(declaration)) {
                changed = true;
            }
        }

        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
            if (rewriteConstructor(constructor)) {
                changed = true;
            }
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (rewriteResponseFlow(method)) {
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : new ArrayList<>(cu.getImports())) {
            String name = importDeclaration.getNameAsString();
            String replacement = null;
            if (name.endsWith(".Client")) {
                replacement = "redis.clients.jedis.Connection";
            } else if (name.endsWith(".BasicRedisPipeline") || name.endsWith(".BinaryRedisPipeline")
                    || name.endsWith(".BinaryScriptingCommandsPipeline") || name.endsWith(".ClusterPipeline")
                    || name.endsWith(".MultiKeyBinaryRedisPipeline") || name.endsWith(".MultiKeyCommandsPipeline")
                    || name.endsWith(".RedisPipeline") || name.endsWith(".ScriptingCommandsPipeline")) {
                replacement = "redis.clients.jedis.commands.PipelineCommands";
            }

            if (replacement != null) {
                cu.getImports().remove(importDeclaration);
                cu.addImport(replacement);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteJedisInheritance(ClassOrInterfaceDeclaration declaration) {
        if (!declaration.isInterface()) {
            return false;
        }

        List<ClassOrInterfaceType> current = new ArrayList<>(declaration.getExtendedTypes());
        boolean containsOldJedis = current.stream().map(Main::qualifiedOrSimpleName).anyMatch(OLD_JEDIS_MARKERS::contains);

        if (!containsOldJedis) {
            return false;
        }

        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (ClassOrInterfaceType type : current) {
            String name = qualifiedOrSimpleName(type);
            if (!OLD_JEDIS_MARKERS.contains(name)) {
                types.add(type.getNameWithScope());
            }
        }

        List<String> replacements = declaration.getNameAsString().toLowerCase().contains("transaction")
                ? TRANSACTION_INTERFACES
                : PIPELINE_INTERFACES;

        types.addAll(replacements);

        declaration.getExtendedTypes().clear();
        for (String type : types) {
            declaration.addExtendedType(type);
        }

        return true;
    }

    private static boolean rewriteConstructor(ConstructorDeclaration constructor) {
        boolean changed = false;
        if (!constructor.getBody().isEmpty()) {
            Statement first = constructor.getBody().getStatement(0);
            Optional<MethodCallExpr> call = asMethodCall(first, "setClient");
            if (call.isPresent() && call.get().getScope().isPresent()) {
                String code = "super(" + call.get().getArgument(0) + ");";
                constructor.getBody().getStatements().set(0, StaticJavaParser.parseStatement(code));
                changed = true;
            }
        }

        constructor.getParameters().forEach(param -> {
            if ("Client".equals(param.getType().asString())) {
                param.setType("Connection");
            }
        });

        return changed;
    }

    private static boolean rewriteResponseFlow(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return false;
        }

        BlockStmt body = method.getBody().get();
        boolean changed = false;

        for (int i = 0; i < body.getStatements().size() - 1; i++) {
            Statement first = body.getStatement(i);
            Statement second = body.getStatement(i + 1);

            Optional<MethodCallExpr> sendCall = asMethodCall(first, "sendCommand", "sendBlockingCommand");
            if (sendCall.isEmpty()) {
                continue;
            }

            Optional<MethodCallExpr> getResponseCall = asGetResponseCall(second);
            if (getResponseCall.isEmpty()) {
                continue;
            }

            Expression appendCommand = buildAppendCommand(sendCall.get(), getResponseCall.get().getArgument(0));

            if (second.isReturnStmt()) {
                body.getStatements().set(i, StaticJavaParser.parseStatement("return " + appendCommand + ";"));
                body.getStatements().remove(i + 1);
                changed = true;
                i = Math.max(-1, i - 1);
                continue;
            }

            if (second.isExpressionStmt()) {
                Expression expression = second.asExpressionStmt().getExpression();
                if (expression.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
                    if (declaration.getVariables().size() == 1 && declaration.getVariable(0).getInitializer().isPresent()) {
                        declaration.getVariable(0).setInitializer(appendCommand);
                        body.getStatements().remove(i);
                        changed = true;
                        i = Math.max(-1, i - 1);
                    }
                }
            }
        }

        return changed;
    }

    private static Expression buildAppendCommand(MethodCallExpr sendCall, Expression builderExpression) {
        String code = "appendCommand(new redis.clients.jedis.CommandObject<>(new redis.clients.jedis.CommandArguments(" +
                sendCall.getArgument(0) + ")";
        for (int i = 1; i < sendCall.getArguments().size(); i++) {
            code += ".add(" + sendCall.getArgument(i) + ")";
        }
        code += ", " + builderExpression + "))";
        return StaticJavaParser.parseExpression(code);
    }

    private static Optional<MethodCallExpr> asGetResponseCall(Statement statement) {
        if (statement.isReturnStmt()) {
            ReturnStmt returnStmt = statement.asReturnStmt();
            if (returnStmt.getExpression().isPresent()) {
                Expression expression = returnStmt.getExpression().get();
                if (expression.isMethodCallExpr() && "getResponse".equals(expression.asMethodCallExpr().getNameAsString())) {
                    return Optional.of(expression.asMethodCallExpr());
                }
            }
        }

        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        Expression expression = statement.asExpressionStmt().getExpression();
        if (expression.isVariableDeclarationExpr()) {
            VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
            if (declaration.getVariables().size() == 1 && declaration.getVariable(0).getInitializer().isPresent()) {
                Expression initializer = declaration.getVariable(0).getInitializer().get();
                if (initializer.isMethodCallExpr() && "getResponse".equals(initializer.asMethodCallExpr().getNameAsString())) {
                    return Optional.of(initializer.asMethodCallExpr());
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<MethodCallExpr> asMethodCall(Statement statement, String... names) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        MethodCallExpr call = expression.asMethodCallExpr();
        if (Arrays.asList(names).contains(call.getNameAsString())) {
            return Optional.of(call);
        }

        return Optional.empty();
    }

    private static String qualifiedOrSimpleName(ClassOrInterfaceType type) {
        return type.getNameWithScope();
    }
}
