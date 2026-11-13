package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path root = Paths.get(args[0]);
        try (var stream = Files.walk(root)) {
            final List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                transformFile(path);
            }
        }
    }

    private static void transformFile(Path path) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(path);
        final Set<Statement> removals = new HashSet<>();
        boolean changed = false;

        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            final List<Statement> statements = new ArrayList<>(block.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                final Statement statement = statements.get(i);
                if (!statement.isExpressionStmt()) {
                    continue;
                }

                final ExpressionStmt expressionStmt = statement.asExpressionStmt();
                if (!expressionStmt.getExpression().isVariableDeclarationExpr()) {
                    continue;
                }

                for (VariableDeclarator variable : expressionStmt.getExpression().asVariableDeclarationExpr().getVariables()) {
                    if (!isFlywayVariable(variable)) {
                        continue;
                    }
                    if (!variable.getInitializer().isPresent() || !isOldFlywayConstruction(variable.getInitializer().get())) {
                        continue;
                    }

                    final Map<String, Expression> captured = captureSetterArguments(block, variable.getNameAsString(), removals);
                    final Expression replacement = StaticJavaParser.parseExpression(buildReplacementExpression(captured));
                    variable.setInitializer(replacement);
                    changed = true;
                }
            }
        }

        if (changed) {
            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                block.getStatements().removeIf(removals::contains);
            }
            Files.writeString(path, cu.toString());
        }
    }

    private static Map<String, Expression> captureSetterArguments(BlockStmt block, String variableName, Set<Statement> removals) {
        final Map<String, Expression> args = new HashMap<>();

        for (MethodCallExpr call : block.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty() || !call.getScope().get().isNameExpr()) {
                continue;
            }
            if (!variableName.equals(call.getScope().get().asNameExpr().getNameAsString())) {
                continue;
            }
            if (call.getArguments().size() != 1) {
                continue;
            }

            final String method = call.getNameAsString();
            if ("setDataSource".equals(method)) {
                args.put("dataSource", call.getArgument(0).clone());
                call.findAncestor(ExpressionStmt.class).ifPresent(removals::add);
            } else if ("setClassLoader".equals(method)) {
                args.put("classLoader", call.getArgument(0).clone());
                call.findAncestor(ExpressionStmt.class).ifPresent(removals::add);
            } else if ("setLocations".equals(method)) {
                args.put("locations", call.getArgument(0).clone());
                call.findAncestor(ExpressionStmt.class).ifPresent(removals::add);
            } else if ("setValidateOnMigrate".equals(method)) {
                args.put("validateOnMigrate", call.getArgument(0).clone());
                call.findAncestor(ExpressionStmt.class).ifPresent(removals::add);
            }
        }

        return args;
    }

    private static String buildReplacementExpression(Map<String, Expression> captured) {
        final StringBuilder builder = new StringBuilder();
        builder.append("org.flywaydb.core.Flyway.configure(");
        if (captured.containsKey("classLoader")) {
            builder.append(captured.get("classLoader"));
        }
        builder.append(")");

        if (captured.containsKey("dataSource")) {
            builder.append(".dataSource(").append(captured.get("dataSource")).append(")");
        }
        if (captured.containsKey("locations")) {
            builder.append(".locations(").append(captured.get("locations")).append(")");
        }
        if (captured.containsKey("validateOnMigrate")) {
            builder.append(".validateOnMigrate(").append(captured.get("validateOnMigrate")).append(")");
        }
        builder.append(".load()");
        return builder.toString();
    }

    private static boolean isFlywayVariable(VariableDeclarator variable) {
        final String type = variable.getType().asString();
        return FLYWAY_TYPE.equals(type) || "Flyway".equals(type) || type.endsWith(".Flyway");
    }

    private static boolean isOldFlywayConstruction(Expression expression) {
        return expression.isObjectCreationExpr() && isOldFlywayConstruction(expression.asObjectCreationExpr());
    }

    private static boolean isOldFlywayConstruction(ObjectCreationExpr creationExpr) {
        final String type = creationExpr.getType().asString();
        return (FLYWAY_TYPE.equals(type) || "Flyway".equals(type) || type.endsWith(".Flyway"))
                && creationExpr.getArguments().isEmpty();
    }
}
