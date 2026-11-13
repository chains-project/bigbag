package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
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
import java.util.stream.Stream;

public class Main {

    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE = "Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            rewriteFlywayConstructionBlocks(compilationUnit);
            rewriteStandaloneFlywayCreations(compilationUnit);

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void rewriteFlywayConstructionBlocks(CompilationUnit compilationUnit) {
        compilationUnit.findAll(BlockStmt.class).forEach(Main::rewriteBlock);
    }

    private static void rewriteStandaloneFlywayCreations(CompilationUnit compilationUnit) {
        compilationUnit.findAll(ObjectCreationExpr.class).stream()
                .filter(Main::isFlywayCreation)
                .filter(expr -> !expr.getParentNode().filter(parent -> parent instanceof VariableDeclarator).isPresent())
                .forEach(expr -> expr.replace(buildFluentFlywayChain(List.of(), false)));
    }

    private static void rewriteBlock(BlockStmt blockStmt) {
        final NodeList<Statement> statements = blockStmt.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            final Optional<VariableDeclarator> variableDeclarator = getFlywayVariableDeclarator(statement);
            if (variableDeclarator.isEmpty()) {
                continue;
            }

            final VariableDeclarator declarator = variableDeclarator.get();
            final String variableName = declarator.getNameAsString();
            final List<MethodCallExpr> setters = new ArrayList<>();
            int j = i + 1;

            while (j < statements.size()) {
                final Statement next = statements.get(j);
                if (!next.isExpressionStmt()) {
                    break;
                }

                final Expression expression = next.asExpressionStmt().getExpression();
                if (!expression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr call = expression.asMethodCallExpr();
                if (!isFlywaySetterOnVariable(call, variableName)) {
                    break;
                }

                setters.add(call.clone());
                j++;
            }

            if (setters.isEmpty()) {
                continue;
            }

            declarator.setInitializer(buildFluentFlywayChain(setters, true));
            for (int k = j - 1; k >= i + 1; k--) {
                statements.remove(k);
            }
        }
    }

    private static Optional<VariableDeclarator> getFlywayVariableDeclarator(Statement statement) {
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

        final VariableDeclarator variableDeclarator = declarationExpr.getVariable(0);
        if (!isFlywayType(variableDeclarator.getType())) {
            return Optional.empty();
        }

        if (variableDeclarator.getInitializer().isEmpty() || !isFlywayCreation(variableDeclarator.getInitializer().get())) {
            return Optional.empty();
        }

        return Optional.of(variableDeclarator);
    }

    private static boolean isFlywayType(Type type) {
        final String typeName = type.asString();
        return FLYWAY_SIMPLE.equals(typeName) || FLYWAY_FQN.equals(typeName);
    }

    private static boolean isFlywayCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final String typeName = expression.asObjectCreationExpr().getType().asString();
        return FLYWAY_SIMPLE.equals(typeName) || FLYWAY_FQN.equals(typeName);
    }

    private static boolean isFlywaySetterOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().filter(scope -> scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(variableName)).isPresent()
                && isSupportedFlywaySetter(call.getNameAsString());
    }

    private static boolean isSupportedFlywaySetter(String name) {
        switch (name) {
            case "setClassLoader":
            case "setDataSource":
            case "setLocations":
            case "setLocationsAsStrings":
            case "setValidateOnMigrate":
                return true;
            default:
                return false;
        }
    }

    private static Expression buildFluentFlywayChain(List<MethodCallExpr> setters, boolean includeLoad) {
        Expression current = StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()");

        for (MethodCallExpr setter : setters) {
            if ("setClassLoader".equals(setter.getNameAsString())) {
                current = StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure(" + setter.getArgument(0).clone() + ")");
                continue;
            }

            final String fluentName = toFluentName(setter.getNameAsString());
            if (fluentName == null) {
                continue;
            }

            current = new MethodCallExpr(current, fluentName, cloneArguments(setter));
        }

        if (includeLoad) {
            current = new MethodCallExpr(current, "load");
        }

        return current;
    }

    private static NodeList<Expression> cloneArguments(MethodCallExpr methodCallExpr) {
        final NodeList<Expression> arguments = new NodeList<>();
        methodCallExpr.getArguments().forEach(argument -> arguments.add(argument.clone()));
        return arguments;
    }

    private static String toFluentName(String setterName) {
        switch (setterName) {
            case "setDataSource":
                return "dataSource";
            case "setLocations":
            case "setLocationsAsStrings":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            default:
                return null;
        }
    }
}
