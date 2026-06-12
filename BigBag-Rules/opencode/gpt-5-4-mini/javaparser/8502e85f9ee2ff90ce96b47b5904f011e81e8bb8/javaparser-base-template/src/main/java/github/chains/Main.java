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
    private static final String FLYWAY_SIMPLE = "Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFileSafely);
        }
    }

    private static void transformFileSafely(Path path) {
        try {
            transformFile(path);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to transform " + path, ex);
        }
    }

    private static void transformFile(Path path) throws IOException {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(Files.readString(path, StandardCharsets.UTF_8));
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;
        for (BlockStmt block : compilationUnit.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }

        if (changed) {
            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean transformBlock(BlockStmt block) {
        final List<Statement> statements = new ArrayList<>(block.getStatements());
        final List<PendingRewrite> rewrites = new ArrayList<>();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);

            final Optional<VariableDeclarator> declaration = getFlywayDeclaration(statement);
            if (declaration.isPresent()) {
                final String variableName = declaration.get().getNameAsString();
                final List<Integer> setterIndexes = collectSetterStatements(statements, i + 1, variableName);
                if (!setterIndexes.isEmpty()) {
                    rewrites.add(new PendingRewrite(i, setterIndexes, buildReplacement(statements, i, setterIndexes)));
                }
            }

            final Optional<AssignExpr> assignment = getFlywayAssignment(statement);
            if (assignment.isPresent()) {
                final String variableName = assignment.get().getTarget().toString();
                final List<Integer> setterIndexes = collectSetterStatements(statements, i + 1, variableName);
                if (!setterIndexes.isEmpty()) {
                    rewrites.add(new PendingRewrite(i, setterIndexes, buildReplacement(statements, i, setterIndexes)));
                }
            }
        }

        if (rewrites.isEmpty()) {
            return false;
        }

        rewrites.sort(Comparator.comparingInt((PendingRewrite rewrite) -> rewrite.declarationIndex).reversed());
        for (PendingRewrite rewrite : rewrites) {
            applyRewrite(block, rewrite);
        }
        return true;
    }

    private static void applyRewrite(BlockStmt block, PendingRewrite rewrite) {
        final Statement declaration = block.getStatements().get(rewrite.declarationIndex);

        if (declaration.isExpressionStmt() && declaration.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
            final VariableDeclarationExpr declarationExpr = declaration.asExpressionStmt().getExpression().asVariableDeclarationExpr();
            declarationExpr.getVariable(0).setInitializer(rewrite.replacement);
        } else if (declaration.isExpressionStmt() && declaration.asExpressionStmt().getExpression().isAssignExpr()) {
            declaration.asExpressionStmt().getExpression().asAssignExpr().setValue(rewrite.replacement);
        }

        for (int i = rewrite.setterIndexes.size() - 1; i >= 0; i--) {
            block.getStatements().remove((int) rewrite.setterIndexes.get(i));
        }
    }

    private static Optional<VariableDeclarator> getFlywayDeclaration(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        final VariableDeclarationExpr declarationExpr = expression.asVariableDeclarationExpr();
        if (declarationExpr.getVariables().size() != 1) {
            return Optional.empty();
        }

        final VariableDeclarator declarator = declarationExpr.getVariable(0);
        return declarator.getInitializer().filter(init -> init.isObjectCreationExpr() && isNoArgFlywayCreation(init.asObjectCreationExpr()))
                .map(init -> declarator);
    }

    private static Optional<AssignExpr> getFlywayAssignment(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isAssignExpr()) {
            return Optional.empty();
        }

        final AssignExpr assignExpr = expression.asAssignExpr();
        return assignExpr.getValue().isObjectCreationExpr() && isNoArgFlywayCreation(assignExpr.getValue().asObjectCreationExpr())
                ? Optional.of(assignExpr)
                : Optional.empty();
    }

    private static List<Integer> collectSetterStatements(List<Statement> statements, int startIndex, String variableName) {
        final List<Integer> indexes = new ArrayList<>();
        for (int i = startIndex; i < statements.size(); i++) {
            if (!isSetterCallOnVariable(statements.get(i), variableName)) {
                break;
            }
            indexes.add(i);
        }
        return indexes;
    }

    private static boolean isSetterCallOnVariable(Statement statement, String variableName) {
        if (!statement.isExpressionStmt()) {
            return false;
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return false;
        }

        final MethodCallExpr call = expression.asMethodCallExpr();
        return call.getScope().map(scope -> scope.toString().equals(variableName)).orElse(false)
                && call.getNameAsString().startsWith("set")
                && call.getNameAsString().length() > 3;
    }

    private static Expression buildReplacement(List<Statement> statements, int declarationIndex, List<Integer> setterIndexes) {
        final List<MethodCallExpr> setterCalls = new ArrayList<>();
        for (int setterIndex : setterIndexes) {
            setterCalls.add(statements.get(setterIndex).asExpressionStmt().getExpression().asMethodCallExpr());
        }

        Expression current = buildConfigureCall(setterCalls);
        for (MethodCallExpr setterCall : setterCalls) {
            if ("setClassLoader".equals(setterCall.getNameAsString())) {
                continue;
            }
            current = new MethodCallExpr(current, toFluentName(setterCall.getNameAsString()), cloneArguments(setterCall.getArguments()));
        }
        return new MethodCallExpr(current, "load");
    }

    private static Expression buildConfigureCall(List<MethodCallExpr> setterCalls) {
        for (MethodCallExpr call : setterCalls) {
            if ("setClassLoader".equals(call.getNameAsString()) && call.getArguments().size() == 1) {
                return new MethodCallExpr(parseExpression(FLYWAY_FQCN), "configure", cloneArguments(call.getArguments()));
            }
        }
        return parseExpression(FLYWAY_FQCN + ".configure()");
    }

    private static String getTargetName(Statement statement) {
        if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
            return statement.asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariable(0).getNameAsString();
        }
        if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isAssignExpr()) {
            return statement.asExpressionStmt().getExpression().asAssignExpr().getTarget().toString();
        }
        throw new IllegalArgumentException("Unexpected declaration statement");
    }

    private static boolean isNoArgFlywayCreation(ObjectCreationExpr creationExpr) {
        return isFlywayType(creationExpr.getTypeAsString()) && creationExpr.getArguments().isEmpty();
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_SIMPLE.equals(typeName) || FLYWAY_FQCN.equals(typeName) || typeName.endsWith("." + FLYWAY_SIMPLE);
    }

    private static Expression parseExpression(String source) {
        return StaticJavaParser.parseExpression(source);
    }

    private static NodeList<Expression> cloneArguments(NodeList<Expression> arguments) {
        final NodeList<Expression> cloned = new NodeList<>();
        for (Expression argument : arguments) {
            cloned.add(argument.clone());
        }
        return cloned;
    }

    private static String toFluentName(String setterName) {
        final String baseName = setterName.substring(3);
        return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
    }

    private static final class PendingRewrite {
        private final int declarationIndex;
        private final List<Integer> setterIndexes;
        private final Expression replacement;

        private PendingRewrite(int declarationIndex, List<Integer> setterIndexes, Expression replacement) {
            this.declarationIndex = declarationIndex;
            this.setterIndexes = setterIndexes;
            this.replacement = replacement;
        }
    }
}
