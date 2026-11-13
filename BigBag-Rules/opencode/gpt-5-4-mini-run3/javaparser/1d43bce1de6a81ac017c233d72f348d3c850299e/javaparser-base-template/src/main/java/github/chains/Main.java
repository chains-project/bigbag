package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;

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
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final String FLUENT_CONFIGURATION_FQCN = "org.flywaydb.core.api.configuration.FluentConfiguration";

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transformFile(path);
                    } catch (final Exception e) {
                        throw new RuntimeException("Failed to transform " + path + ": " + e.getMessage(), e);
                    }
                });
    }

    private static void transformFile(final Path path) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(path);
        final boolean changed = transformCompilationUnit(cu);
        if (changed) {
            Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean transformCompilationUnit(final CompilationUnit cu) {
        boolean changed = false;
        for (final BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(final BlockStmt block) {
        boolean changed = false;
        final List<Statement> statements = new ArrayList<>(block.getStatements());
        final Map<String, ConfigState> trackedVariables = new HashMap<>();

        for (final Statement statement : statements) {
            for (final VariableDeclarator declarator : statement.findAll(VariableDeclarator.class)) {
                if (!isFlywayType(declarator.getType())) {
                    continue;
                }

                if (declarator.getInitializer().isPresent() && isFlywayConstruction(declarator.getInitializer().get())) {
                    trackedVariables.put(declarator.getNameAsString(), new ConfigState(declarator));
                }
            }
        }

        if (trackedVariables.isEmpty()) {
            return false;
        }

        for (final Statement statement : statements) {
            for (final ConfigState state : trackedVariables.values()) {
                if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isMethodCallExpr()) {
                    final MethodCallExpr call = statement.asExpressionStmt().getExpression().asMethodCallExpr();
                    if (isSetterOnTrackedVariable(call, state.variableName) && "setClassLoader".equals(call.getNameAsString())) {
                        state.classLoaderArgument = call.getArgument(0).clone();
                    }
                }
            }
        }

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);

            for (final ConfigState state : trackedVariables.values()) {
                changed |= transformStatementForVariable(statement, state);
            }
        }

        return changed;
    }

    private static boolean transformStatementForVariable(final Statement statement, final ConfigState state) {
        boolean changed = false;

        for (final VariableDeclarator declarator : statement.findAll(VariableDeclarator.class)) {
            if (declarator != state.declarator) {
                continue;
            }

            final Expression initializer = buildConfigureExpression(state);
            declarator.setType(FLUENT_CONFIGURATION_FQCN);
            declarator.setInitializer(initializer);
            changed = true;
        }

        if (statement.isExpressionStmt()) {
            final Expression expression = statement.asExpressionStmt().getExpression();
            if (expression.isMethodCallExpr()) {
                final MethodCallExpr call = expression.asMethodCallExpr();
                if (isSetterOnTrackedVariable(call, state.variableName)) {
                    if ("setClassLoader".equals(call.getNameAsString())) {
                        state.classLoaderArgument = call.getArgument(0).clone();
                        statement.remove();
                        return true;
                    }

                    call.setName(normalizeSetterName(call.getNameAsString()));
                    changed = true;
                }
            }
        }

        if (statement.isReturnStmt()) {
            final ReturnStmt returnStmt = statement.asReturnStmt();
            if (returnsFlyway(returnStmt) && isNameOfTrackedVariable(returnStmt.getExpression(), state.variableName)) {
                returnStmt.setExpression(new MethodCallExpr(new NameExpr(state.variableName), "load"));
                changed = true;
            }
        }

        return changed;
    }

    private static Expression buildConfigureExpression(final ConfigState state) {
        final MethodCallExpr configure = StaticJavaParser.parseExpression(FLYWAY_FQCN + ".configure()").asMethodCallExpr();
        if (state.classLoaderArgument != null) {
            configure.addArgument(state.classLoaderArgument.clone());
        }
        return configure;
    }

    private static boolean isFlywayConstruction(final Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }
        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        return creationExpr.getType().getNameAsString().equals("Flyway") || creationExpr.getType().asString().endsWith(".Flyway");
    }

    private static boolean isFlywayType(final Type type) {
        final String typeName = type.asString();
        return "Flyway".equals(typeName) || FLYWAY_FQCN.equals(typeName) || typeName.endsWith(".Flyway");
    }

    private static boolean isSetterOnTrackedVariable(final MethodCallExpr call, final String variableName) {
        return call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString())
                && call.getNameAsString().startsWith("set")
                && call.getArguments().size() == 1;
    }

    private static boolean isNameOfTrackedVariable(final Optional<Expression> expression, final String variableName) {
        return expression.isPresent()
                && expression.get().isNameExpr()
                && variableName.equals(expression.get().asNameExpr().getNameAsString());
    }

    private static boolean returnsFlyway(final ReturnStmt returnStmt) {
        final Optional<MethodDeclaration> method = returnStmt.findAncestor(MethodDeclaration.class);
        return method.map(declaration -> isFlywayType(declaration.getType())).orElse(false);
    }

    private static String normalizeSetterName(final String oldName) {
        String candidate = oldName;
        if (candidate.startsWith("set") && candidate.length() > 3) {
            candidate = candidate.substring(3);
        }

        for (final String suffix : new String[] {"AsStrings", "AsString", "AsFileName", "AsFile", "AsClassNames"}) {
            if (candidate.endsWith(suffix)) {
                candidate = candidate.substring(0, candidate.length() - suffix.length());
                break;
            }
        }

        if (candidate.isEmpty()) {
            return oldName;
        }

        return Character.toLowerCase(candidate.charAt(0)) + candidate.substring(1);
    }

    private static final class ConfigState {
        private final VariableDeclarator declarator;
        private final String variableName;
        private Expression classLoaderArgument;

        private ConfigState(final VariableDeclarator declarator) {
            this.declarator = declarator;
            this.variableName = declarator.getNameAsString();
        }
    }
}
