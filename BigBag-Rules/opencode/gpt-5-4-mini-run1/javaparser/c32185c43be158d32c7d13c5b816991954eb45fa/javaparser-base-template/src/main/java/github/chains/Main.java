package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final Set<String> OLD_SETTERS = Set.of(
            "setDataSource",
            "setClassLoader",
            "setLocations",
            "setValidateOnMigrate"
    );

    public static void main(String[] args) {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            if (rewrite(compilationUnit)) {
                Files.writeString(path, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean rewrite(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (BlockStmt block : compilationUnit.findAll(BlockStmt.class)) {
            changed |= rewriteBlock(block);
        }
        return changed;
    }

    private static boolean rewriteBlock(BlockStmt block) {
        final NodeList<Statement> statements = block.getStatements();
        final List<Statement> removals = new ArrayList<>();
        boolean changed = false;

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);

            if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
                final VariableDeclarationExpr declaration = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr();
                for (VariableDeclarator variable : declaration.getVariables()) {
                    final Optional<Rewrite> rewrite = buildRewrite(variable.getInitializer().orElse(null), variable.getNameAsString(), statements, i + 1);
                    if (rewrite.isPresent()) {
                        variable.setInitializer(rewrite.get().expression);
                        removals.addAll(rewrite.get().removedStatements);
                        changed = true;
                    }
                }
            } else if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isAssignExpr()) {
                final AssignExpr assignExpr = statement.asExpressionStmt().getExpression().asAssignExpr();
                if (assignExpr.getTarget().isNameExpr()) {
                    final Optional<Rewrite> rewrite = buildRewrite(assignExpr.getValue(), assignExpr.getTarget().asNameExpr().getNameAsString(), statements, i + 1);
                    if (rewrite.isPresent()) {
                        assignExpr.setValue(rewrite.get().expression);
                        removals.addAll(rewrite.get().removedStatements);
                        changed = true;
                    }
                }
            }
        }

        if (!removals.isEmpty()) {
            final Set<Statement> removalSet = new HashSet<>(removals);
            statements.removeIf(removalSet::contains);
        }

        return changed;
    }

    private static Optional<Rewrite> buildRewrite(Expression initializer, String variableName, NodeList<Statement> statements, int startIndex) {
        if (!isOldFlywayCreation(initializer)) {
            return Optional.empty();
        }

        final List<Statement> removedStatements = new ArrayList<>();
        final List<MethodCallExpr> calls = new ArrayList<>();
        Expression classLoader = null;

        for (int i = startIndex; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                break;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr call = expression.asMethodCallExpr();
            if (!isCallOnVariable(call, variableName) || !OLD_SETTERS.contains(call.getNameAsString())) {
                break;
            }

            removedStatements.add(statement);
            if ("setClassLoader".equals(call.getNameAsString())) {
                classLoader = call.getArguments().isEmpty() ? null : call.getArgument(0);
            } else {
                calls.add(call);
            }
        }

        final StringBuilder builder = new StringBuilder("org.flywaydb.core.Flyway.configure(");
        if (classLoader != null) {
            builder.append(classLoader);
        }
        builder.append(")");
        for (MethodCallExpr call : calls) {
            builder.append('.').append(toBuilderMethod(call.getNameAsString())).append('(')
                    .append(call.getArguments().stream().map(Expression::toString).collect(Collectors.joining(", ")))
                    .append(')');
        }
        builder.append(".load()");

        return Optional.of(new Rewrite(StaticJavaParser.parseExpression(builder.toString()), removedStatements));
    }

    private static boolean isOldFlywayCreation(Expression expression) {
        if (expression == null || !expression.isObjectCreationExpr()) {
            return false;
        }
        final ObjectCreationExpr creation = expression.asObjectCreationExpr();
        final String typeName = creation.getType().asString();
        return "Flyway".equals(typeName) || FLYWAY_TYPE.equals(typeName);
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static String toBuilderMethod(String setterName) {
        return setterName.startsWith("set") && setterName.length() > 3
                ? Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4)
                : setterName;
    }

    private static final class Rewrite {
        private final Expression expression;
        private final List<Statement> removedStatements;

        private Rewrite(Expression expression, List<Statement> removedStatements) {
            this.expression = expression;
            this.removedStatements = removedStatements;
        }
    }
}
