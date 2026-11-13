package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE_NAME = "Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source root " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            if (transformCompilationUnit(cu)) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        final List<Statement> original = new ArrayList<>(block.getStatements());
        final NodeList<Statement> rewritten = new NodeList<>();
        boolean changed = false;

        for (int i = 0; i < original.size(); i++) {
            final Statement statement = original.get(i);
            final Optional<VariableDeclarator> declaration = findFlywayDeclaration(statement);
            if (declaration.isPresent()) {
                final String variableName = declaration.get().getNameAsString();
                final Chain chain = collectChain(original, i + 1, variableName);
                if (chain.hasAnyTransformation()) {
                    declaration.get().setInitializer(StaticJavaParser.parseExpression(chain.asExpression()));
                    changed = true;
                    i = chain.lastConsumedIndex;
                }
            }

            rewritten.add(statement);
        }

        if (changed) {
            block.setStatements(rewritten);
        }

        return changed;
    }

    private static Optional<VariableDeclarator> findFlywayDeclaration(Statement statement) {
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

        final VariableDeclarator variable = declarationExpr.getVariable(0);
        if (!isFlywayType(variable.getType().asString())) {
            return Optional.empty();
        }

        if (!variable.getInitializer().isPresent() || !isFlywayCreation(variable.getInitializer().get())) {
            return Optional.empty();
        }

        return Optional.of(variable);
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_SIMPLE_NAME.equals(typeName) || FLYWAY_FQCN.equals(typeName);
    }

    private static boolean isFlywayCreation(Expression expression) {
        return expression.isObjectCreationExpr() && isFlywayType(expression.asObjectCreationExpr().getType().asString());
    }

    private static Chain collectChain(List<Statement> statements, int startIndex, String variableName) {
        String classLoaderArg = null;
        final List<MethodCallExpr> fluentCalls = new ArrayList<>();
        int index = startIndex;

        while (index < statements.size()) {
            final Statement next = statements.get(index);
            if (!next.isExpressionStmt()) {
                break;
            }

            final Expression expr = next.asExpressionStmt().getExpression();
            if (!expr.isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr call = expr.asMethodCallExpr();
            if (!isCallOnVariable(call, variableName)) {
                break;
            }

            final String methodName = call.getNameAsString();
            switch (methodName) {
                case "setClassLoader":
                    classLoaderArg = call.getArguments().isEmpty() ? null : call.getArgument(0).toString();
                    break;
                case "setDataSource":
                    fluentCalls.add(new MethodCallExpr(null, "dataSource", copyArguments(call)));
                    break;
                case "setLocations":
                case "setLocationsAsStrings":
                    fluentCalls.add(new MethodCallExpr(null, "locations", copyArguments(call)));
                    break;
                case "setValidateOnMigrate":
                    fluentCalls.add(new MethodCallExpr(null, "validateOnMigrate", copyArguments(call)));
                    break;
                default:
                    return new Chain(classLoaderArg, fluentCalls, index - 1, false);
            }

            index++;
        }

        return new Chain(classLoaderArg, fluentCalls, index - 1, !fluentCalls.isEmpty() || classLoaderArg != null);
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope()
            .filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr())
            .map(scope -> scope.isNameExpr()
                ? scope.asNameExpr().getNameAsString()
                : scope.asFieldAccessExpr().getNameAsString())
            .map(variableName::equals)
            .orElse(false);
    }

    private static NodeList<Expression> copyArguments(MethodCallExpr call) {
        final NodeList<Expression> copied = new NodeList<>();
        for (Expression argument : call.getArguments()) {
            copied.add(argument.clone());
        }
        return copied;
    }

    private static final class Chain {
        private final String classLoaderArg;
        private final List<MethodCallExpr> fluentCalls;
        private final int lastConsumedIndex;
        private final boolean transformed;

        private Chain(String classLoaderArg, List<MethodCallExpr> fluentCalls, int lastConsumedIndex, boolean transformed) {
            this.classLoaderArg = classLoaderArg;
            this.fluentCalls = fluentCalls;
            this.lastConsumedIndex = lastConsumedIndex;
            this.transformed = transformed;
        }

        private boolean hasAnyTransformation() {
            return transformed;
        }

        private String asExpression() {
            final StringBuilder expression = new StringBuilder();
            expression.append(FLYWAY_FQCN).append(".configure(");
            if (classLoaderArg != null) {
                expression.append(classLoaderArg);
            }
            expression.append(")");

            for (MethodCallExpr call : fluentCalls) {
                expression.append('.').append(call.getNameAsString()).append('(').append(joinArgs(call.getArguments())).append(')');
            }

            expression.append(".load()");
            return expression.toString();
        }
    }

    private static String joinArgs(NodeList<Expression> args) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(args.get(i));
        }
        return builder.toString();
    }
}
