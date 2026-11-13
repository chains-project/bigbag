package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class Main {

    private static final String OLD_CLIENT = "redis.clients.jedis.Client";
    private static final String NEW_CONNECTION = "redis.clients.jedis.Connection";
    private static final Set<String> OLD_PIPELINE_TYPES = Set.of(
            "BasicRedisPipeline",
            "BinaryRedisPipeline",
            "BinaryScriptingCommandsPipeline",
            "ClusterPipeline",
            "MultiKeyBinaryRedisPipeline",
            "MultiKeyCommandsPipeline",
            "RedisPipeline",
            "ScriptingCommandsPipeline"
    );
    private static final List<String> NEW_PIPELINE_TYPES = List.of(
            "redis.clients.jedis.commands.PipelineCommands",
            "redis.clients.jedis.commands.PipelineBinaryCommands",
            "redis.clients.jedis.commands.RedisModulePipelineCommands"
    );

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir>");
        }

        Path root = Paths.get(args[0]);
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            changed |= rewriteImports(cu);
            changed |= rewriteInterfaces(cu);
            changed |= rewriteConstructors(cu);
            changed |= rewriteMethodCalls(cu);
            changed |= rewriteJedisClientTypes(cu);
            changed |= rewriteLegacyClientFieldUses(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        boolean needsConnectionImport = false;
        boolean needsPipelineImports = false;

        for (int i = cu.getImports().size() - 1; i >= 0; i--) {
            String name = cu.getImports().get(i).getNameAsString();
            if (OLD_CLIENT.equals(name)) {
                cu.getImports().get(i).setName(NEW_CONNECTION);
                changed = true;
                needsConnectionImport = true;
            }
            if (OLD_PIPELINE_TYPES.contains(simpleName(name))) {
                cu.getImports().remove(i);
                changed = true;
                needsPipelineImports = true;
            }
        }

        if (needsConnectionImport && cu.getImports().stream().noneMatch(i -> NEW_CONNECTION.equals(i.getNameAsString()))) {
            cu.addImport(NEW_CONNECTION);
        }
        if (needsPipelineImports) {
            for (String fqcn : NEW_PIPELINE_TYPES) {
                if (cu.getImports().stream().noneMatch(i -> fqcn.equals(i.getNameAsString()))) {
                    cu.addImport(fqcn);
                }
            }
        }

        return changed || needsConnectionImport || needsPipelineImports;
    }

    private static boolean rewriteInterfaces(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!decl.isInterface()) {
                continue;
            }

            boolean touchesLegacy = decl.getExtendedTypes().stream()
                    .anyMatch(t -> OLD_PIPELINE_TYPES.contains(t.getNameAsString()));
            if (!touchesLegacy) {
                continue;
            }

            decl.getExtendedTypes().removeIf(t -> OLD_PIPELINE_TYPES.contains(t.getNameAsString()));
            for (String fqcn : NEW_PIPELINE_TYPES) {
                String simple = simpleName(fqcn);
                if (decl.getExtendedTypes().stream().noneMatch(t -> simple.equals(t.getNameAsString()))) {
                    decl.addExtendedType(simple);
                }
            }
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteConstructors(CompilationUnit cu) {
        boolean changed = false;

        for (ConstructorDeclaration ctor : cu.findAll(ConstructorDeclaration.class)) {
            boolean classNeedsConnection = ctor.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(Main::isPipelineOrTransactionImplementation)
                    .orElse(false);

            for (Parameter parameter : ctor.getParameters()) {
                if (OLD_CLIENT.equals(parameter.getType().asString()) || "Client".equals(parameter.getType().asString())) {
                    parameter.setType("Connection");
                    changed = true;
                }
            }

            if (!classNeedsConnection) {
                continue;
            }

            List<Statement> statements = ctor.getBody().getStatements();
            if (statements.isEmpty() || !statements.get(0).isExpressionStmt()) {
                continue;
            }

            Expression expr = statements.get(0).asExpressionStmt().getExpression();
            if (!expr.isMethodCallExpr()) {
                continue;
            }

            MethodCallExpr call = expr.asMethodCallExpr();
            if (call.getScope().isPresent()
                    && call.getScope().get().isSuperExpr()
                    && "setClient".equals(call.getNameAsString())
                    && !call.getArguments().isEmpty()) {
                Statement replacement = StaticJavaParser.parseStatement("super(" + call.getArgument(0).toString() + ");");
                statements.set(0, replacement);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("getResponse".equals(call.getNameAsString())) {
                call.setName("enqueResponse");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteJedisClientTypes(CompilationUnit cu) {
        boolean changed = false;
        for (Parameter parameter : cu.findAll(Parameter.class)) {
            if (OLD_CLIENT.equals(parameter.getType().asString()) || "Client".equals(parameter.getType().asString())) {
                parameter.setType(NEW_CONNECTION);
                changed = true;
            }
        }
        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (OLD_CLIENT.equals(variable.getType().asString()) || "Client".equals(variable.getType().asString())) {
                variable.setType(NEW_CONNECTION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteLegacyClientFieldUses(CompilationUnit cu) {
        boolean changed = false;
        for (NameExpr expr : cu.findAll(NameExpr.class)) {
            if (!"client".equals(expr.getNameAsString())) {
                continue;
            }
            if (!expr.findAncestor(ClassOrInterfaceDeclaration.class).map(Main::isPipelineOrTransactionImplementation).orElse(false)) {
                continue;
            }
            expr.setName("connection");
            changed = true;
        }

        cu.findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class).forEach(field -> {
            if ("client".equals(field.getNameAsString()) && field.findAncestor(ClassOrInterfaceDeclaration.class).map(Main::isPipelineOrTransactionImplementation).orElse(false)) {
                field.setName("connection");
            }
        });
        return changed;
    }

    private static boolean isPipelineOrTransactionImplementation(ClassOrInterfaceDeclaration decl) {
        for (ClassOrInterfaceType type : decl.getExtendedTypes()) {
            String name = type.getNameAsString();
            if ("Pipeline".equals(name) || "Transaction".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }
}
