package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static final String OLD_FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath();
        Files.walk(sourceRoot)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = transformCompilationUnit(cu);
            if (changed) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + path, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] changed = {false};
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getBody().isPresent() && transformMethodBody(method.getBody().get())) {
                changed[0] = true;
            }
        }
        return changed[0];
    }

    private static boolean transformMethodBody(BlockStmt body) {
        boolean changed = false;
        Map<String, FlywayConfig> configs = new HashMap<>();
        List<Statement> statements = new ArrayList<>(body.getStatements());
        List<Statement> toRemove = new ArrayList<>();

        for (Statement statement : statements) {
            Optional<VariableDeclarator> maybeVar = findFlywayVariable(statement);
            if (maybeVar.isPresent()) {
                VariableDeclarator var = maybeVar.get();
                String varName = var.getNameAsString();
                configs.put(varName, new FlywayConfig(var));
                continue;
            }

            Optional<MethodCallExpr> maybeCall = findSetterCall(statement);
            if (maybeCall.isPresent()) {
                MethodCallExpr call = maybeCall.get();
                String scopeName = call.getScope().map(Node::toString).orElse(null);
                FlywayConfig config = configs.get(scopeName);
                if (config != null && config.consumeSetter(call)) {
                    toRemove.add(statement);
                    changed = true;
                }
            }
        }

        if (configs.isEmpty()) {
            return changed;
        }

        for (FlywayConfig config : configs.values()) {
            if (config.applyIfComplete()) {
                changed = true;
            }
        }

        body.getStatements().removeAll(toRemove);
        return changed;
    }

    private static Optional<VariableDeclarator> findFlywayVariable(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }
        Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
        if (declaration.getVariables().size() != 1) {
            return Optional.empty();
        }

        VariableDeclarator var = declaration.getVariable(0);
        if (var.getInitializer().isEmpty()) {
            return Optional.empty();
        }

        Expression initializer = var.getInitializer().get();
        if (!initializer.isObjectCreationExpr()) {
            return Optional.empty();
        }

        ObjectCreationExpr creation = initializer.asObjectCreationExpr();
        String typeName = creation.getType().toString();
        if (!OLD_FLYWAY_FQCN.equals(typeName) && !"Flyway".equals(typeName)) {
            return Optional.empty();
        }
        if (!creation.getArguments().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(var);
    }

    private static Optional<MethodCallExpr> findSetterCall(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }
        Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }
        return Optional.of(expression.asMethodCallExpr());
    }

    private static final class FlywayConfig {
        private final VariableDeclarator variable;
        private Expression dataSourceArg;
        private Expression classLoaderArg;
        private Expression locationsArg;
        private Expression validateOnMigrateArg;

        private FlywayConfig(VariableDeclarator variable) {
            this.variable = variable;
        }

        private boolean consumeSetter(MethodCallExpr call) {
            if (call.getArguments().size() != 1) {
                return false;
            }

            String name = call.getNameAsString();
            Expression arg = call.getArgument(0).clone();
            switch (name) {
                case "setDataSource":
                    this.dataSourceArg = arg;
                    return true;
                case "setClassLoader":
                    this.classLoaderArg = arg;
                    return true;
                case "setLocations":
                    this.locationsArg = arg;
                    return true;
                case "setValidateOnMigrate":
                    this.validateOnMigrateArg = arg;
                    return true;
                default:
                    return false;
            }
        }

        private boolean applyIfComplete() {
            if (this.dataSourceArg == null && this.classLoaderArg == null && this.locationsArg == null && this.validateOnMigrateArg == null) {
                return false;
            }

            StringBuilder expr = new StringBuilder();
            expr.append(OLD_FLYWAY_FQCN).append(".configure(");
            if (this.classLoaderArg != null) {
                expr.append(this.classLoaderArg);
            }
            expr.append(")");
            if (this.dataSourceArg != null) {
                expr.append(".dataSource(").append(this.dataSourceArg).append(")");
            }
            if (this.locationsArg != null) {
                expr.append(".locations(").append(this.locationsArg).append(")");
            }
            if (this.validateOnMigrateArg != null) {
                expr.append(".validateOnMigrate(").append(this.validateOnMigrateArg).append(")");
            }
            expr.append(".load()");

            this.variable.setInitializer(StaticJavaParser.parseExpression(expr.toString()));
            return true;
        }
    }
}
