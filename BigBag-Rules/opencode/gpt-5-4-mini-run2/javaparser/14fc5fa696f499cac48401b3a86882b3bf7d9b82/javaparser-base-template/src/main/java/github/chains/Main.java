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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::transformFile);
        }
    }

    private static boolean isJavaSource(final Path path) {
        final String normalized = path.toString();
        return Files.isRegularFile(path)
                && normalized.endsWith(".java")
                && !normalized.contains("/target/")
                && !normalized.contains("/build/");
    }

    private static void transformFile(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            final boolean changed = transformCompilationUnit(cu);
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformCompilationUnit(final CompilationUnit cu) {
        boolean changed = false;
        final List<BlockStmt> blocks = cu.findAll(BlockStmt.class);
        for (final BlockStmt block : blocks) {
            final NodeList<Statement> original = block.getStatements();
            final NodeList<Statement> rewritten = new NodeList<>();
            boolean blockChanged = false;

            for (int i = 0; i < original.size(); ) {
                final Optional<Replacement> replacement = rewriteFlywayDeclaration(original, i);
                if (replacement.isPresent()) {
                    rewritten.add(replacement.get().statement);
                    i = replacement.get().nextIndex;
                    blockChanged = true;
                } else {
                    rewritten.add(original.get(i).clone());
                    i++;
                }
            }

            if (blockChanged) {
                block.setStatements(rewritten);
                changed = true;
            }
        }
        return changed;
    }

    private static Optional<Replacement> rewriteFlywayDeclaration(final NodeList<Statement> statements, final int index) {
        final Statement statement = statements.get(index);
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        final var declaration = expression.asVariableDeclarationExpr();
        if (declaration.getVariables().size() != 1) {
            return Optional.empty();
        }

        final VariableDeclarator variable = declaration.getVariable(0);
        if (!isFlywayType(variable.getType().asString())) {
            return Optional.empty();
        }

        final Optional<Expression> initializer = variable.getInitializer();
        if (initializer.isEmpty() || !isNoArgFlywayConstruction(initializer.get())) {
            return Optional.empty();
        }

        final String variableName = variable.getNameAsString();
        final List<MethodCallExpr> fluentCalls = new ArrayList<>();
        Expression classLoaderArgument = null;
        int nextIndex = index + 1;

        while (nextIndex < statements.size()) {
            final Statement nextStatement = statements.get(nextIndex);
            if (!nextStatement.isExpressionStmt()) {
                break;
            }

            final Expression nextExpression = nextStatement.asExpressionStmt().getExpression();
            if (!nextExpression.isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr methodCall = nextExpression.asMethodCallExpr();
            if (!isMethodCallOnVariable(methodCall, variableName)) {
                break;
            }

            final String methodName = methodCall.getNameAsString();
            if ("setClassLoader".equals(methodName)) {
                if (methodCall.getArguments().size() == 1) {
                    classLoaderArgument = methodCall.getArgument(0).clone();
                    nextIndex++;
                    continue;
                }
                break;
            }

            final String fluentName = convertSetterName(methodName);
            if (fluentName == null) {
                break;
            }

            fluentCalls.add(new MethodCallExpr(null, fluentName, cloneArguments(methodCall.getArguments())));
            nextIndex++;
        }

        final MethodCallExpr configuredFlyway = buildFlywayConfiguration(classLoaderArgument, fluentCalls);
        final Statement rewrittenStatement = statement.clone();
        final VariableDeclarator rewrittenVariable = rewrittenStatement.asExpressionStmt()
                .getExpression()
                .asVariableDeclarationExpr()
                .getVariable(0);
        rewrittenVariable.setInitializer(configuredFlyway);

        return Optional.of(new Replacement(rewrittenStatement, nextIndex));
    }

    private static boolean isFlywayType(final String typeName) {
        return FLYWAY_FQCN.equals(typeName) || typeName.endsWith(".Flyway") || "Flyway".equals(typeName);
    }

    private static boolean isNoArgFlywayConstruction(final Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creation = expression.asObjectCreationExpr();
        return isFlywayType(creation.getType().asString()) && creation.getArguments().isEmpty();
    }

    private static boolean isMethodCallOnVariable(final MethodCallExpr methodCall, final String variableName) {
        return methodCall.getScope()
                .filter(Expression::isNameExpr)
                .map(Expression::asNameExpr)
                .map(NameExpr::getNameAsString)
                .filter(variableName::equals)
                .isPresent();
    }

    private static String convertSetterName(final String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return null;
        }

        String suffix = setterName.substring(3);
        for (final String removableSuffix : new String[]{"AsClassNames", "AsStrings", "AsString", "AsFileName", "AsFile"}) {
            if (suffix.endsWith(removableSuffix)) {
                suffix = suffix.substring(0, suffix.length() - removableSuffix.length());
                break;
            }
        }

        if (suffix.isEmpty()) {
            return null;
        }

        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static MethodCallExpr buildFlywayConfiguration(final Expression classLoaderArgument,
                                                           final List<MethodCallExpr> fluentCalls) {
        MethodCallExpr expression = StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()");
        if (classLoaderArgument != null) {
            expression.addArgument(classLoaderArgument);
        }

        for (final MethodCallExpr fluentCall : fluentCalls) {
            expression = new MethodCallExpr(expression, fluentCall.getNameAsString(), cloneArguments(fluentCall.getArguments()));
        }

        return new MethodCallExpr(expression, "load");
    }

    private static NodeList<Expression> cloneArguments(final NodeList<Expression> arguments) {
        final NodeList<Expression> cloned = new NodeList<>();
        for (final Expression argument : arguments) {
            cloned.add(argument.clone());
        }
        return cloned;
    }

    private static final class Replacement {
        private final Statement statement;
        private final int nextIndex;

        private Replacement(final Statement statement, final int nextIndex) {
            this.statement = statement;
            this.nextIndex = nextIndex;
        }
    }
}
