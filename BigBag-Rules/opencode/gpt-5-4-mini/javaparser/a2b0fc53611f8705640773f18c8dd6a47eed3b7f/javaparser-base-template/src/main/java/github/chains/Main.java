package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String FLUENT_CONFIGURATION_TYPE = "org.flywaydb.core.api.configuration.FluentConfiguration";

    private static final Map<String, String> SETTER_TO_FLUENT = new HashMap<>();

    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setLocationsAsStrings", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
    }

    public static void main(String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        final ParserConfiguration parserConfiguration = new ParserConfiguration();
        StaticJavaParser.setConfiguration(parserConfiguration);

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(final Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (final BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= transformBlock(block);
            }

            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformBlock(final BlockStmt block) {
        boolean changed = false;
        final List<Statement> statements = block.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final ExpressionStmt expressionStmt = statement.asExpressionStmt();
            final Expression expression = expressionStmt.getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            final VariableDeclarationExpr declarationExpr = expression.asVariableDeclarationExpr();
            if (declarationExpr.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator declarator = declarationExpr.getVariable(0);
            if (!isFlywayType(declarator.getType().asString())) {
                continue;
            }
            if (!declarator.getInitializer().isPresent() || !isNewFlyway(declarator.getInitializer().get())) {
                continue;
            }

            final String variableName = declarator.getNameAsString();
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            Expression classLoaderArgument = null;

            int j = i + 1;
            while (j < statements.size()) {
                final Statement next = statements.get(j);
                final Optional<MethodCallExpr> call = extractSetterCall(next, variableName);
                if (!call.isPresent()) {
                    break;
                }

                final MethodCallExpr methodCall = call.get();
                if ("setClassLoader".equals(methodCall.getNameAsString())) {
                    if (methodCall.getArguments().size() == 1) {
                        classLoaderArgument = methodCall.getArgument(0).clone();
                    }
                } else if (SETTER_TO_FLUENT.containsKey(methodCall.getNameAsString())) {
                    setterCalls.add(methodCall);
                }
                j++;
            }

            final Expression replacement = buildReplacement(classLoaderArgument, setterCalls);
            declarator.setInitializer(replacement);

            for (int k = j - 1; k > i; k--) {
                statements.get(k).remove();
            }

            changed = true;
        }

        return changed;
    }

    private static Expression buildReplacement(final Expression classLoaderArgument, final List<MethodCallExpr> setterCalls) {
        final String configureCall = classLoaderArgument == null
                ? "org.flywaydb.core.Flyway.configure()"
                : "org.flywaydb.core.Flyway.configure(" + classLoaderArgument + ")";

        Expression expression = StaticJavaParser.parseExpression(configureCall);
        for (final MethodCallExpr setterCall : setterCalls) {
            expression = appendFluentCall(expression, setterCall);
        }
        return appendLoadCall(expression);
    }

    private static Expression appendFluentCall(final Expression expression, final MethodCallExpr setterCall) {
        final String fluentMethod = SETTER_TO_FLUENT.get(setterCall.getNameAsString());
        if (fluentMethod == null) {
            return expression;
        }

        final MethodCallExpr fluentCall = new MethodCallExpr(expression.clone(), fluentMethod);
        for (final Expression argument : setterCall.getArguments()) {
            fluentCall.addArgument(argument.clone());
        }
        return fluentCall;
    }

    private static Expression appendLoadCall(final Expression expression) {
        return new MethodCallExpr(expression, "load");
    }

    private static Optional<MethodCallExpr> extractSetterCall(final Statement statement, final String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr methodCall = expression.asMethodCallExpr();
        if (!methodCall.getScope().isPresent() || !methodCall.getScope().get().isNameExpr()) {
            return Optional.empty();
        }

        final NameExpr scope = methodCall.getScope().get().asNameExpr();
        if (!variableName.equals(scope.getNameAsString())) {
            return Optional.empty();
        }

        if ("setClassLoader".equals(methodCall.getNameAsString()) || SETTER_TO_FLUENT.containsKey(methodCall.getNameAsString())) {
            return Optional.of(methodCall);
        }

        return Optional.empty();
    }

    private static boolean isFlywayType(final String typeName) {
        return FLYWAY_TYPE.equals(typeName) || typeName.endsWith(".Flyway") || "Flyway".equals(typeName);
    }

    private static boolean isNewFlyway(final Expression expression) {
        return expression.isObjectCreationExpr() && isFlywayType(expression.asObjectCreationExpr().getType().asString())
                && expression.asObjectCreationExpr().getArguments().isEmpty();
    }
}
