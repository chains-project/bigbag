package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE = "Flyway";

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path path) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            compilationUnit.findAll(BlockStmt.class).forEach(Main::rewriteBlock);

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static void rewriteBlock(BlockStmt block) {
        final List<Statement> statements = block.getStatements();
        int index = 0;

        while (index < statements.size()) {
            final Statement statement = statements.get(index);
            if (!statement.isExpressionStmt()) {
                index++;
                continue;
            }

            final ExpressionStmt expressionStmt = statement.asExpressionStmt();
            if (!expressionStmt.getExpression().isVariableDeclarationExpr()) {
                index++;
                continue;
            }

            final VariableDeclarator declarator = getFlywayDeclarator(expressionStmt);
            if (declarator == null || !declarator.getInitializer().isPresent()) {
                index++;
                continue;
            }

            final Expression initializer = declarator.getInitializer().get();
            if (!isOldFlywayCreation(initializer)) {
                index++;
                continue;
            }

            final List<MethodCallExpr> setters = collectTrailingSetters(statements, index + 1, declarator.getNameAsString());
            final Expression replacement = buildFlywayConfiguration(setters);
            declarator.setInitializer(replacement);

            for (int i = 0; i < setters.size(); i++) {
                statements.remove(index + 1);
            }

            index++;
        }
    }

    private static VariableDeclarator getFlywayDeclarator(ExpressionStmt expressionStmt) {
        final List<VariableDeclarator> variables = expressionStmt.getExpression().asVariableDeclarationExpr().getVariables();
        if (variables.size() != 1) {
            return null;
        }

        final VariableDeclarator variable = variables.get(0);
        return isFlywayType(variable.getType().asString()) ? variable : null;
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_SIMPLE.equals(typeName) || FLYWAY_TYPE.equals(typeName) || typeName.endsWith("." + FLYWAY_SIMPLE);
    }

    private static boolean isOldFlywayCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        return isFlywayType(creationExpr.getType().asString()) && creationExpr.getArguments().isEmpty();
    }

    private static List<MethodCallExpr> collectTrailingSetters(List<Statement> statements, int startIndex, String variableName) {
        final List<MethodCallExpr> setters = new ArrayList<>();

        for (int i = startIndex; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                break;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr methodCall = expression.asMethodCallExpr();
            if (!methodCall.getScope().isPresent() || !methodCall.getScope().get().isNameExpr()) {
                break;
            }

            if (!variableName.equals(methodCall.getScope().get().asNameExpr().getNameAsString())) {
                break;
            }

            if (!methodCall.getNameAsString().startsWith("set") || methodCall.getArguments().size() != 1) {
                break;
            }

            setters.add(methodCall);
        }

        return setters;
    }

    private static Expression buildFlywayConfiguration(List<MethodCallExpr> setters) {
        Expression expression = createConfigureExpression(setters);

        for (MethodCallExpr setter : setters) {
            final String methodName = setter.getNameAsString().substring(3);
            if ("ClassLoader".equals(methodName)) {
                continue;
            }

            final String fluentName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
            expression = new MethodCallExpr(expression, fluentName, new NodeList<>(setter.getArgument(0).clone()));
        }

        return new MethodCallExpr(expression, "load");
    }

    private static Expression createConfigureExpression(List<MethodCallExpr> setters) {
        for (MethodCallExpr setter : setters) {
            if ("setClassLoader".equals(setter.getNameAsString())) {
                return new MethodCallExpr(new NameExpr("Flyway"), "configure", new NodeList<>(setter.getArgument(0).clone()));
            }
        }

        return new MethodCallExpr(new NameExpr("Flyway"), "configure");
    }
}
