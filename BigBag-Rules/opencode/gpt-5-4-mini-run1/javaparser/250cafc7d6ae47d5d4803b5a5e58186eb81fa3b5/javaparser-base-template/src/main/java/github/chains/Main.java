package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        final FlywayTransformer transformer = new FlywayTransformer();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            final List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                final String original = Files.readString(path, StandardCharsets.UTF_8);
                final CompilationUnit cu = StaticJavaParser.parse(original);
                final CompilationUnit updated = (CompilationUnit) cu.accept(transformer, null);
                final String rewritten = updated.toString();
                if (!rewritten.equals(original)) {
                    Files.writeString(path, rewritten, StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static final class FlywayTransformer extends ModifierVisitor<Void> {

        @Override
        public Visitable visit(BlockStmt block, Void arg) {
            final List<Statement> originalStatements = new ArrayList<>(block.getStatements());
            final List<Statement> rewrittenStatements = new ArrayList<>();

            for (int i = 0; i < originalStatements.size(); i++) {
                final Statement statement = originalStatements.get(i);
                final Optional<RewritePlan> plan = tryRewrite(originalStatements, i);
                if (plan.isPresent()) {
                    rewrittenStatements.add((Statement) plan.get().replacement.accept(this, arg));
                    i = plan.get().lastConsumedIndex;
                } else {
                    rewrittenStatements.add((Statement) statement.accept(this, arg));
                }
            }

            block.setStatements(new NodeList<>(rewrittenStatements));
            return block;
        }

        private Optional<RewritePlan> tryRewrite(List<Statement> statements, int index) {
            final Statement statement = statements.get(index);
            if (!statement.isExpressionStmt()) {
                return Optional.empty();
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                return Optional.empty();
            }

            final VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
            if (declaration.getVariables().size() != 1) {
                return Optional.empty();
            }

            final VariableDeclarator variable = declaration.getVariable(0);
            if (!variable.getInitializer().isPresent() || !isFlywayConstruction(variable.getInitializer().get())) {
                return Optional.empty();
            }

            final String variableName = variable.getNameAsString();
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            int cursor = index + 1;
            while (cursor < statements.size()) {
                final Optional<MethodCallExpr> methodCall = extractSetterCall(statements.get(cursor), variableName);
                if (!methodCall.isPresent()) {
                    break;
                }
                setterCalls.add(methodCall.get());
                cursor++;
            }

            final VariableDeclarationExpr rewrittenDeclaration = declaration.clone();
            rewrittenDeclaration.getVariable(0).setInitializer(buildFluentInitializer(variable, setterCalls));
            final Statement rewrittenStatement = new ExpressionStmt(rewrittenDeclaration);
            return Optional.of(new RewritePlan(rewrittenStatement, cursor - 1));
        }

        private boolean isFlywayConstruction(Expression expression) {
            if (!expression.isObjectCreationExpr()) {
                return false;
            }
            final ObjectCreationExpr creation = expression.asObjectCreationExpr();
            final String typeName = creation.getType().toString();
            return FLYWAY_FQN.equals(typeName) || "Flyway".equals(typeName) || typeName.endsWith(".Flyway");
        }

        private Optional<MethodCallExpr> extractSetterCall(Statement statement, String variableName) {
            if (!statement.isExpressionStmt()) {
                return Optional.empty();
            }
            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                return Optional.empty();
            }

            final MethodCallExpr methodCall = expression.asMethodCallExpr();
            if (!methodCall.getScope().isPresent() || !matchesTarget(methodCall.getScope().get(), variableName)) {
                return Optional.empty();
            }

            final String mapped = mapSetterName(methodCall.getNameAsString());
            return mapped == null ? Optional.empty() : Optional.of(methodCall);
        }

        private boolean matchesTarget(Expression scope, String variableName) {
            if (scope.isNameExpr()) {
                return variableName.equals(scope.asNameExpr().getNameAsString());
            }
            if (scope.isFieldAccessExpr()) {
                final FieldAccessExpr fieldAccess = scope.asFieldAccessExpr();
                return variableName.equals(fieldAccess.getNameAsString())
                        || (fieldAccess.getScope().isThisExpr() && variableName.equals(fieldAccess.getNameAsString()));
            }
            return false;
        }

        private Expression buildFluentInitializer(VariableDeclarator variable, List<MethodCallExpr> setterCalls) {
            final String flywayQualifier = FLYWAY_FQN;

            Expression classLoaderArg = null;
            for (MethodCallExpr setterCall : setterCalls) {
                if ("setClassLoader".equals(setterCall.getNameAsString()) && !setterCall.getArguments().isEmpty()) {
                    classLoaderArg = setterCall.getArgument(0).clone();
                    break;
                }
            }

            final Expression base = classLoaderArg == null
                    ? StaticJavaParser.parseExpression(flywayQualifier + ".configure()")
                    : StaticJavaParser.parseExpression(flywayQualifier + ".configure(" + classLoaderArg + ")");

            Expression chain = base;
            for (MethodCallExpr setterCall : setterCalls) {
                final String mapped = mapSetterName(setterCall.getNameAsString());
                if (mapped == null || "setClassLoader".equals(setterCall.getNameAsString())) {
                    continue;
                }
                final String args = setterCall.getArguments().stream()
                        .map(Expression::toString)
                        .collect(Collectors.joining(", "));
                chain = StaticJavaParser.parseExpression(chain + "." + mapped + "(" + args + ")");
            }

            return StaticJavaParser.parseExpression(chain + ".load()");
        }

        private String mapSetterName(String methodName) {
            switch (methodName) {
                case "setDataSource":
                    return "dataSource";
                case "setLocations":
                case "setLocationsAsStrings":
                    return "locations";
                case "setValidateOnMigrate":
                    return "validateOnMigrate";
                case "setClassLoader":
                    return "configure";
                default:
                    return null;
            }
        }

        private static final class RewritePlan {
            private final Statement replacement;
            private final int lastConsumedIndex;

            private RewritePlan(Statement replacement, int lastConsumedIndex) {
                this.replacement = replacement;
                this.lastConsumedIndex = lastConsumedIndex;
            }
        }
    }
}
