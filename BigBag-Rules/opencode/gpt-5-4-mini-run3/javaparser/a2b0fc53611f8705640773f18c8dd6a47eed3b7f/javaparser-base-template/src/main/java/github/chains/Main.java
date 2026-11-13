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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root path.");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            if (transformCompilationUnit(compilationUnit)) {
                Files.writeString(file, compilationUnit.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (BlockStmt blockStmt : compilationUnit.findAll(BlockStmt.class)) {
            changed |= transformBlock(blockStmt);
        }
        return changed;
    }

    private static boolean transformBlock(BlockStmt blockStmt) {
        boolean changed = false;
        final NodeList<Statement> statements = blockStmt.getStatements();

        for (int index = 0; index < statements.size(); index++) {
            final Statement statement = statements.get(index);
            final Optional<String> variableName = getFlywayVariableName(statement);
            if (!variableName.isPresent()) {
                continue;
            }

            final VariableDeclarator variableDeclarator = getVariableDeclarator(statement).get();
            final TransformResult result = buildReplacement(statements, index + 1, variableName.get());
            if (result == null) {
                continue;
            }

            variableDeclarator.setInitializer(result.replacement);
            removeRange(statements, result.fromIndex, result.toIndexExclusive);
            changed = true;
        }

        for (int index = 0; index < statements.size(); index++) {
            final Statement statement = statements.get(index);
            final Optional<AssignExpr> assignExpr = getFlywayAssignment(statement);
            if (!assignExpr.isPresent()) {
                continue;
            }

            final String variableName = assignExpr.get().getTarget().toString();
            final TransformResult result = buildReplacement(statements, index + 1, variableName);
            if (result == null) {
                continue;
            }

            assignExpr.get().setValue(result.replacement);
            removeRange(statements, result.fromIndex, result.toIndexExclusive);
            changed = true;
        }

        return changed;
    }

    private static TransformResult buildReplacement(NodeList<Statement> statements, int startIndex, String variableName) {
        Expression classLoader = null;
        final List<MethodCallExpr> fluentCalls = new ArrayList<>();
        int index = startIndex;

        while (index < statements.size()) {
            final Optional<MethodCallExpr> setter = getSetterCall(statements.get(index), variableName);
            if (!setter.isPresent()) {
                break;
            }

            final String oldMethod = setter.get().getNameAsString();
            if ("setClassLoader".equals(oldMethod)) {
                classLoader = setter.get().getArgument(0);
            } else {
                fluentCalls.add(toFluentCall(setter.get()));
            }
            index++;
        }

        if (fluentCalls.isEmpty() && classLoader == null) {
            return null;
        }

        final Expression replacement = buildFlywayLoadExpression(classLoader, fluentCalls);
        return new TransformResult(startIndex, index, replacement);
    }

    private static Expression buildFlywayLoadExpression(Expression classLoader, List<MethodCallExpr> fluentCalls) {
        Expression expression = classLoader == null
                ? StaticJavaParser.parseExpression(FLYWAY_FQCN + ".configure()")
                : StaticJavaParser.parseExpression(FLYWAY_FQCN + ".configure(" + classLoader + ")");

        for (MethodCallExpr call : fluentCalls) {
            expression = new MethodCallExpr(expression, call.getNameAsString(), call.getArguments());
        }

        return new MethodCallExpr(expression, "load");
    }

    private static MethodCallExpr toFluentCall(MethodCallExpr setterCall) {
        final String methodName = setterCall.getNameAsString();
        final String fluentName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        return new MethodCallExpr(null, fluentName, setterCall.getArguments());
    }

    private static Optional<String> getFlywayVariableName(Statement statement) {
        final Optional<VariableDeclarator> variableDeclarator = getVariableDeclarator(statement);
        if (!variableDeclarator.isPresent() || !variableDeclarator.get().getInitializer().isPresent()) {
            return Optional.empty();
        }

        final Expression initializer = variableDeclarator.get().getInitializer().get();
        if (initializer instanceof ObjectCreationExpr && isFlywayCreation((ObjectCreationExpr) initializer)) {
            return Optional.of(variableDeclarator.get().getNameAsString());
        }

        return Optional.empty();
    }

    private static Optional<VariableDeclarator> getVariableDeclarator(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!(expression instanceof VariableDeclarationExpr)) {
            return Optional.empty();
        }

        final VariableDeclarationExpr declarationExpr = (VariableDeclarationExpr) expression;
        if (declarationExpr.getVariables().size() != 1) {
            return Optional.empty();
        }
        return Optional.of(declarationExpr.getVariable(0));
    }

    private static Optional<AssignExpr> getFlywayAssignment(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!(expression instanceof AssignExpr)) {
            return Optional.empty();
        }

        final AssignExpr assignExpr = (AssignExpr) expression;
        if (assignExpr.getValue() instanceof ObjectCreationExpr && isFlywayCreation((ObjectCreationExpr) assignExpr.getValue())) {
            return Optional.of(assignExpr);
        }
        return Optional.empty();
    }

    private static Optional<MethodCallExpr> getSetterCall(Statement statement, String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!(expression instanceof MethodCallExpr)) {
            return Optional.empty();
        }

        final MethodCallExpr methodCallExpr = (MethodCallExpr) expression;
        if (!methodCallExpr.getScope().isPresent() || !methodCallExpr.getScope().get().isNameExpr()) {
            return Optional.empty();
        }

        if (!methodCallExpr.getScope().get().asNameExpr().getNameAsString().equals(variableName)) {
            return Optional.empty();
        }

        final String name = methodCallExpr.getNameAsString();
        if (!name.equals("setDataSource") && !name.equals("setClassLoader") && !name.equals("setLocations") && !name.equals("setValidateOnMigrate")) {
            return Optional.empty();
        }

        return Optional.of(methodCallExpr);
    }

    private static boolean isFlywayCreation(ObjectCreationExpr creationExpr) {
        return FLYWAY_FQCN.equals(creationExpr.getType().asString()) || "Flyway".equals(creationExpr.getType().asString());
    }

    private static void removeRange(NodeList<Statement> statements, int fromIndex, int toIndexExclusive) {
        for (int i = toIndexExclusive - 1; i >= fromIndex; i--) {
            statements.remove(i);
        }
    }

    private static final class TransformResult {
        private final int fromIndex;
        private final int toIndexExclusive;
        private final Expression replacement;

        private TransformResult(int fromIndex, int toIndexExclusive, Expression replacement) {
            this.fromIndex = fromIndex;
            this.toIndexExclusive = toIndexExclusive;
            this.replacement = replacement;
        }
    }
}
