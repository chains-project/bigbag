package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE_NAME = "Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected a single source directory argument");
        }

        final Path root = Paths.get(args[0]);
        final List<Path> javaFiles;
        try (var paths = Files.walk(root)) {
            javaFiles = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            final CompilationUnit cu = StaticJavaParser.parse(Files.readString(file, StandardCharsets.UTF_8));
            LexicalPreservingPrinter.setup(cu);
            if (rewriteFlywayUsage(cu)) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean rewriteFlywayUsage(final CompilationUnit cu) {
        boolean changed = false;
        final List<BlockStmt> blocks = cu.findAll(BlockStmt.class);
        for (BlockStmt block : blocks) {
            changed |= rewriteBlock(block);
        }

        if (changed && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(FLYWAY_TYPE))) {
            cu.addImport(FLYWAY_TYPE);
        }

        return changed;
    }

    private static boolean rewriteBlock(final BlockStmt block) {
        final List<Statement> statements = new ArrayList<>(block.getStatements());
        final Map<String, FlywayState> states = new HashMap<>();
        boolean changed = false;

        for (Statement statement : statements) {
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (expression.isVariableDeclarationExpr()) {
                final VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
                for (VariableDeclarator var : declaration.getVariables()) {
                    if (isOldFlywayInstantiation(var)) {
                        states.put(var.getNameAsString(), new FlywayState(var));
                        changed = true;
                    }
                }
                continue;
            }

            if (!expression.isMethodCallExpr()) {
                continue;
            }

            final MethodCallExpr call = expression.asMethodCallExpr();
            final Optional<String> scopeName = getScopeName(call.getScope().orElse(null));
            if (!scopeName.isPresent()) {
                continue;
            }

            final FlywayState state = states.get(scopeName.get());
            if (state == null) {
                continue;
            }

            if (call.getNameAsString().equals("setClassLoader") && call.getArguments().size() == 1) {
                state.classLoaderArgument = call.getArgument(0).clone();
                state.setterStatements.add(statement);
                changed = true;
                continue;
            }

            if (call.getNameAsString().startsWith("set") && call.getNameAsString().length() > 3) {
                state.builderCalls.add(callToBuilderCall(call));
                state.setterStatements.add(statement);
                changed = true;
            }
        }

        for (FlywayState state : states.values()) {
            if (state.classLoaderArgument == null && state.builderCalls.isEmpty()) {
                continue;
            }

            state.declarator.setInitializer(buildFlywayBuilder(state));
            for (Statement setterStatement : state.setterStatements) {
                setterStatement.remove();
            }
            changed = true;
        }

        return changed;
    }

    private static boolean isOldFlywayInstantiation(final VariableDeclarator var) {
        return var.getInitializer().filter(Expression::isObjectCreationExpr).map(Expression::asObjectCreationExpr)
                .filter(Main::isFlywayType).filter(expr -> expr.getArguments().isEmpty()).isPresent();
    }

    private static boolean isFlywayType(final ObjectCreationExpr expr) {
        final String typeName = expr.getType().asString();
        return FLYWAY_SIMPLE_NAME.equals(typeName) || FLYWAY_TYPE.equals(typeName) || typeName.endsWith("." + FLYWAY_SIMPLE_NAME);
    }

    private static Optional<String> getScopeName(final Expression scope) {
        if (scope == null) {
            return Optional.empty();
        }

        if (scope.isNameExpr()) {
            return Optional.of(scope.asNameExpr().getNameAsString());
        }

        if (scope.isFieldAccessExpr()) {
            final FieldAccessExpr field = scope.asFieldAccessExpr();
            if (field.getScope().isThisExpr()) {
                return Optional.of(field.getNameAsString());
            }
        }

        return Optional.empty();
    }

    private static MethodCallExpr callToBuilderCall(final MethodCallExpr call) {
        return new MethodCallExpr(null, toBuilderMethodName(call.getNameAsString()), cloneArguments(call));
    }

    private static String toBuilderMethodName(final String setterName) {
        if (setterName.startsWith("set") && setterName.length() > 3) {
            final String stripped = setterName.substring(3);
            return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
        }
        return setterName;
    }

    private static NodeList<Expression> cloneArguments(final MethodCallExpr call) {
        final NodeList<Expression> args = new NodeList<>();
        for (Expression argument : call.getArguments()) {
            args.add(argument.clone());
        }
        return args;
    }

    private static NodeList<Expression> singletonNodeList(final Expression expression) {
        final NodeList<Expression> args = new NodeList<>();
        args.add(expression);
        return args;
    }

    private static Expression buildFlywayBuilder(final FlywayState state) {
        Expression builder = state.classLoaderArgument == null
                ? new MethodCallExpr(new NameExpr(FLYWAY_SIMPLE_NAME), "configure")
                : new MethodCallExpr(new NameExpr(FLYWAY_SIMPLE_NAME), "configure", singletonNodeList(state.classLoaderArgument.clone()));

        for (MethodCallExpr builderCall : state.builderCalls) {
            builder = new MethodCallExpr(builder, builderCall.getNameAsString(), cloneArguments(builderCall));
        }

        return new MethodCallExpr(builder, "load");
    }

    private static final class FlywayState {
        private final VariableDeclarator declarator;
        private final List<Statement> setterStatements = new ArrayList<>();
        private final List<MethodCallExpr> builderCalls = new ArrayList<>();
        private Expression classLoaderArgument;

        private FlywayState(final VariableDeclarator declarator) {
            this.declarator = declarator;
        }
    }
}
