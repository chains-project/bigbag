package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::processFile);
        }
    }

    private static void processFile(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= transformBlock(block);
            }

            for (ReturnStmt returnStmt : cu.findAll(ReturnStmt.class)) {
                changed |= transformReturn(returnStmt);
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        final List<ExpressionStmt> removals = new ArrayList<>();

        for (int i = 0; i < block.getStatements().size(); i++) {
            if (!(block.getStatement(i) instanceof ExpressionStmt)) {
                continue;
            }

            final ExpressionStmt stmt = block.getStatement(i).asExpressionStmt();
            final Expression expr = stmt.getExpression();
            if (!(expr instanceof VariableDeclarationExpr)) {
                continue;
            }

            final VariableDeclarationExpr declaration = expr.asVariableDeclarationExpr();
            for (VariableDeclarator variable : declaration.getVariables()) {
                if (!isFlywayType(variable.getType().asString())) {
                    continue;
                }
                if (variable.getInitializer().isEmpty() || !isFlywayConstruction(variable.getInitializer().get())) {
                    continue;
                }

                final FlywayRewrite rewrite = buildRewrite(block, i, variable.getNameAsString());
                if (rewrite == null) {
                    continue;
                }

                variable.setInitializer(rewrite.initializer);
                removals.addAll(rewrite.removals);
                changed = true;
            }
        }

        if (changed) {
            removals.forEach(block.getStatements()::remove);
        }

        return changed;
    }

    private static FlywayRewrite buildRewrite(BlockStmt block, int declarationIndex, String variableName) {
        Expression classLoaderArg = null;
        final List<SetterCall> setterCalls = new ArrayList<>();
        final List<ExpressionStmt> removals = new ArrayList<>();

        for (int i = declarationIndex + 1; i < block.getStatements().size(); i++) {
            if (!(block.getStatement(i) instanceof ExpressionStmt)) {
                continue;
            }

            final ExpressionStmt stmt = block.getStatement(i).asExpressionStmt();
            final Expression expr = stmt.getExpression();
            if (!(expr instanceof MethodCallExpr)) {
                continue;
            }

            final MethodCallExpr call = expr.asMethodCallExpr();
            if (!isCallOnVariable(call, variableName) || !call.getNameAsString().startsWith("set")) {
                continue;
            }

            if ("setClassLoader".equals(call.getNameAsString())) {
                if (call.getArguments().isEmpty()) {
                    continue;
                }
                classLoaderArg = call.getArgument(0).clone();
                removals.add(stmt);
                continue;
            }

            setterCalls.add(new SetterCall(call.getNameAsString().substring(3), cloneArgs(call)));
            removals.add(stmt);
        }

        final Expression initializer = buildInitializer(classLoaderArg, setterCalls);
        return new FlywayRewrite(initializer, removals);
    }

    private static boolean transformReturn(ReturnStmt returnStmt) {
        final Optional<Expression> expression = returnStmt.getExpression();
        if (expression.isEmpty() || !isFlywayConstruction(expression.get())) {
            return false;
        }

        returnStmt.setExpression(buildInitializer(null, List.of()));
        return true;
    }

    private static Expression buildInitializer(Expression classLoaderArg, List<SetterCall> setterCalls) {
        Expression expression = StaticJavaParser.parseExpression(
                classLoaderArg == null
                        ? "org.flywaydb.core.Flyway.configure()"
                        : "org.flywaydb.core.Flyway.configure(" + classLoaderArg + ")");

        for (SetterCall setterCall : setterCalls) {
            final String fluentName = toFluentName(setterCall.setterName);
            if (fluentName == null) {
                continue;
            }
            expression = new MethodCallExpr(expression, fluentName, setterCall.arguments);
        }

        return new MethodCallExpr(expression, "load");
    }

    private static boolean isFlywayConstruction(Expression expression) {
        if (!(expression instanceof ObjectCreationExpr)) {
            return false;
        }
        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        return isFlywayType(creationExpr.getType().asString()) && creationExpr.getArguments().isEmpty();
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_FQCN.equals(typeName) || "Flyway".equals(typeName) || typeName.endsWith(".Flyway");
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope()
                .filter(scope -> scope.isNameExpr() && variableName.equals(scope.asNameExpr().getNameAsString()))
                .isPresent();
    }

    private static NodeList<Expression> cloneArgs(NodeWithArguments<?> node) {
        final NodeList<Expression> args = new NodeList<>();
        for (Expression arg : node.getArguments()) {
            args.add(arg.clone());
        }
        return args;
    }

    private static String toFluentName(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return null;
        }
        final String suffix = setterName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static final class SetterCall {
        private final String setterName;
        private final NodeList<Expression> arguments;

        private SetterCall(String setterName, NodeList<Expression> arguments) {
            this.setterName = setterName;
            this.arguments = arguments;
        }
    }

    private static final class FlywayRewrite {
        private final Expression initializer;
        private final List<ExpressionStmt> removals;

        private FlywayRewrite(Expression initializer, List<ExpressionStmt> removals) {
            this.initializer = initializer;
            this.removals = removals;
        }
    }
}
