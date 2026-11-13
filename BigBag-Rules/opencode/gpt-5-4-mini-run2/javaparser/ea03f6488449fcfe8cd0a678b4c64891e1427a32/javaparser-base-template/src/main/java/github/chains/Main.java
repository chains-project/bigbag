package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String OLD_CLIENT = "redis.clients.jedis.Client";
    private static final String NEW_CLIENT = "redis.clients.jedis.Connection";
    private static final String COMMAND_ARGUMENTS = "redis.clients.jedis.CommandArguments";
    private static final String COMMAND_OBJECT = "redis.clients.jedis.CommandObject";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8));

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> transform(parser, path));
    }

    private static void transform(JavaParser parser, Path path) {
        try {
            Optional<CompilationUnit> parsed = parser.parse(path).getResult();
            if (!parsed.isPresent()) {
                return;
            }

            CompilationUnit cu = parsed.get();
            boolean changed = false;
            changed |= rewriteClientTypes(cu);
            changed |= rewriteOldPipelineInterfaces(cu);
            changed |= rewriteConstructors(cu);
            changed |= rewriteCommandSequences(cu);
            changed |= rewriteMultiCalls(cu);

            if (changed) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteClientTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if ("Client".equals(type.getNameAsString())) {
                type.setName("Connection");
                changed = true;
            }
        }

        if (removeImport(cu, OLD_CLIENT)) {
            cu.addImport(NEW_CLIENT);
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteOldPipelineInterfaces(CompilationUnit cu) {
        boolean changed = false;
        boolean needsPipelineCommands = false;
        boolean needsPipelineBinaryCommands = false;
        boolean needsRedisModulePipelineCommands = false;

        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (replaceInterfaceList(decl.getExtendedTypes())) {
                needsPipelineCommands = true;
                needsPipelineBinaryCommands = true;
                needsRedisModulePipelineCommands = true;
                changed = true;
            }
            if (replaceInterfaceList(decl.getImplementedTypes())) {
                needsPipelineCommands = true;
                needsPipelineBinaryCommands = true;
                needsRedisModulePipelineCommands = true;
                changed = true;
            }
        }

        if (needsPipelineCommands) {
            addImportIfMissing(cu, "redis.clients.jedis.commands.PipelineCommands");
        }
        if (needsPipelineBinaryCommands) {
            addImportIfMissing(cu, "redis.clients.jedis.commands.PipelineBinaryCommands");
        }
        if (needsRedisModulePipelineCommands) {
            addImportIfMissing(cu, "redis.clients.jedis.commands.RedisModulePipelineCommands");
        }
        if (needsPipelineCommands || needsPipelineBinaryCommands || needsRedisModulePipelineCommands) {
            changed = true;
        }

        if (removeOldPipelineInterfaceImports(cu)) {
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteConstructors(CompilationUnit cu) {
        boolean changed = false;
        for (ConstructorDeclaration ctor : cu.findAll(ConstructorDeclaration.class)) {
            if (ctor.getParameters().isEmpty()) {
                continue;
            }

            List<Statement> statements = ctor.getBody().getStatements();
            if (statements.isEmpty()) {
                continue;
            }

            Statement first = statements.get(0);
            if (!first.isExpressionStmt()) {
                continue;
            }

            Expression expr = first.asExpressionStmt().getExpression();
            if (!expr.isMethodCallExpr()) {
                continue;
            }

            MethodCallExpr call = expr.asMethodCallExpr();
            if (!"setClient".equals(call.getNameAsString())) {
                continue;
            }

            if (!call.getScope().isPresent() || !"super".equals(call.getScope().get().toString())) {
                continue;
            }

            statements.set(0, StaticJavaParser.parseStatement("super(" + call.getArgument(0) + ");"));
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteCommandSequences(CompilationUnit cu) {
        boolean changed = false;
        boolean needsCommandArgumentsImport = false;
        boolean needsCommandObjectImport = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            for (int i = 0; i < block.getStatements().size() - 1; i++) {
                Statement current = block.getStatement(i);
                Optional<MethodCallExpr> send = asCall(current, "sendCommand");
                if (!send.isPresent()) {
                    continue;
                }

                Statement next = block.getStatement(i + 1);
                Optional<MethodCallExpr> response = getResponseCall(next);
                if (!response.isPresent()) {
                    continue;
                }

                Expression replacement = executeCommandExpr(send.get(), response.get().getArgument(0));
                needsCommandArgumentsImport = true;
                needsCommandObjectImport = true;
                if (next.isReturnStmt()) {
                    block.setStatement(i, new ReturnStmt(replacement));
                    block.getStatements().remove(i + 1);
                } else if (next.isExpressionStmt() && next.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
                    VariableDeclarationExpr decl = next.asExpressionStmt().getExpression().asVariableDeclarationExpr();
                    if (decl.getVariables().size() == 1 && decl.getVariable(0).getInitializer().isPresent()) {
                        decl.getVariable(0).setInitializer(replacement);
                        block.getStatements().remove(i);
                    }
                } else if (next.isExpressionStmt() && next.asExpressionStmt().getExpression().isAssignExpr()) {
                    next.asExpressionStmt().getExpression().asAssignExpr().setValue(replacement);
                    block.getStatements().remove(i);
                } else {
                    continue;
                }

                changed = true;
                i = Math.max(-1, i - 1);
            }
        }

        if (needsCommandArgumentsImport) {
            addImportIfMissing(cu, COMMAND_ARGUMENTS);
            changed = true;
        }
        if (needsCommandObjectImport) {
            addImportIfMissing(cu, COMMAND_OBJECT);
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteMultiCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if ("multi".equals(call.getNameAsString()) && call.getArguments().isEmpty()) {
                    call.setName("transaction");
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Optional<MethodCallExpr> asCall(Statement statement, String name) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }
        Expression expr = statement.asExpressionStmt().getExpression();
        if (!expr.isMethodCallExpr()) {
            return Optional.empty();
        }
        MethodCallExpr call = expr.asMethodCallExpr();
        return name.equals(call.getNameAsString()) ? Optional.of(call) : Optional.empty();
    }

    private static Optional<MethodCallExpr> getResponseCall(Statement statement) {
        if (statement.isReturnStmt()) {
            Optional<Expression> expr = statement.asReturnStmt().getExpression();
            if (expr.isPresent() && expr.get().isMethodCallExpr()) {
                MethodCallExpr call = expr.get().asMethodCallExpr();
                if ("getResponse".equals(call.getNameAsString())) {
                    return Optional.of(call);
                }
            }
            return Optional.empty();
        }

        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        Expression expr = statement.asExpressionStmt().getExpression();
        if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr decl = expr.asVariableDeclarationExpr();
            if (decl.getVariables().size() == 1 && decl.getVariable(0).getInitializer().isPresent()) {
                Expression init = decl.getVariable(0).getInitializer().get();
                if (init.isMethodCallExpr() && "getResponse".equals(init.asMethodCallExpr().getNameAsString())) {
                    return Optional.of(init.asMethodCallExpr());
                }
            }
        }

        if (expr.isAssignExpr()) {
            Expression value = expr.asAssignExpr().getValue();
            if (value.isMethodCallExpr() && "getResponse".equals(value.asMethodCallExpr().getNameAsString())) {
                return Optional.of(value.asMethodCallExpr());
            }
        }

        return Optional.empty();
    }

    private static Expression executeCommandExpr(MethodCallExpr send, Expression builder) {
        StringBuilder commandArgs = new StringBuilder("new CommandArguments(").append(send.getArgument(0)).append(")");
        if (send.getArguments().size() > 1) {
            commandArgs.append(".addObjects(");
            for (int i = 1; i < send.getArguments().size(); i++) {
                if (i > 1) {
                    commandArgs.append(", ");
                }
                commandArgs.append(send.getArgument(i));
            }
            commandArgs.append(")");
        }

        String expr = "executeCommand(new CommandObject(" + commandArgs + ", " + builder + "))";
        return StaticJavaParser.parseExpression(expr);
    }

    private static boolean replaceInterfaceList(NodeList<ClassOrInterfaceType> types) {
        boolean changed = false;
        for (int i = 0; i < types.size(); i++) {
            String name = types.get(i).getNameAsString();
            if (isOldPipelineInterface(name)) {
                types.remove(i);
                i--;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isOldPipelineInterface(String name) {
        return "BasicRedisPipeline".equals(name)
                || "BinaryRedisPipeline".equals(name)
                || "BinaryScriptingCommandsPipeline".equals(name)
                || "ClusterPipeline".equals(name)
                || "MultiKeyBinaryRedisPipeline".equals(name)
                || "MultiKeyCommandsPipeline".equals(name)
                || "RedisPipeline".equals(name)
                || "ScriptingCommandsPipeline".equals(name);
    }

    private static void addImportIfMissing(CompilationUnit cu, String name) {
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(name))) {
            cu.addImport(name);
        }
    }

    private static boolean removeImport(CompilationUnit cu, String name) {
        return cu.getImports().removeIf(i -> i.getNameAsString().equals(name));
    }

    private static boolean removeOldPipelineInterfaceImports(CompilationUnit cu) {
        return cu.getImports().removeIf(i -> isOldPipelineInterface(i.getNameAsString()));
    }
}
