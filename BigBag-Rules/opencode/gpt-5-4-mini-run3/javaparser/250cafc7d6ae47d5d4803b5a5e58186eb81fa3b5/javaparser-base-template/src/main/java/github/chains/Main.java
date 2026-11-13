package github.chains;

import com.github.javaparser.ParseResult;
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
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }

        final Path sourceRootPath = Paths.get(args[0]);
        final SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        sourceRoot.setPrinter(LexicalPreservingPrinter::print);

        final List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParseParallelized();
        for (final ParseResult<CompilationUnit> parseResult : parseResults) {
            parseResult.getResult().ifPresent(cu -> {
                LexicalPreservingPrinter.setup(cu);
                transformCompilationUnit(cu);
            });
        }

        sourceRoot.saveAll(sourceRootPath);
    }

    private static void transformCompilationUnit(final CompilationUnit cu) {
        cu.findAll(BlockStmt.class).forEach(Main::rewriteFlywayBlocks);
        cu.findAll(ObjectCreationExpr.class).forEach(Main::rewriteDirectFlywayConstruction);
    }

    private static void rewriteDirectFlywayConstruction(final ObjectCreationExpr creationExpr) {
        if (!isFlywayNoArgConstruction(creationExpr)) {
            return;
        }

        creationExpr.replace(StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure().load()"));
    }

    private static void rewriteFlywayBlocks(final BlockStmt blockStmt) {
        final List<Statement> originalStatements = new ArrayList<>(blockStmt.getStatements());
        final List<Statement> rewrittenStatements = new ArrayList<>();

        for (int i = 0; i < originalStatements.size(); i++) {
            final Statement statement = originalStatements.get(i);
            final Optional<FlywayAnchor> anchor = findFlywayAnchor(statement);
            if (!anchor.isPresent()) {
                rewrittenStatements.add(statement);
                continue;
            }

            final List<SetterInvocation> setters = new ArrayList<>();
            int nextIndex = i + 1;
            while (nextIndex < originalStatements.size()) {
                final Statement next = originalStatements.get(nextIndex);
                final Optional<SetterInvocation> setter = extractSetter(next, anchor.get().targetText);
                if (!setter.isPresent()) {
                    break;
                }
                setters.add(setter.get());
                nextIndex++;
            }

            if (setters.isEmpty()) {
                continue;
            }

            final String builderText = buildFlywayExpression(setters);
            final Expression replacement = StaticJavaParser.parseExpression(builderText);

            anchor.get().replaceInitializer(replacement);
            rewrittenStatements.add(statement);
            i = nextIndex - 1;
            continue;
        }

        blockStmt.getStatements().clear();
        blockStmt.getStatements().addAll(rewrittenStatements);
    }

    private static Optional<FlywayAnchor> findFlywayAnchor(final Statement statement) {
        if (statement.isExpressionStmt()) {
            final Expression expression = statement.asExpressionStmt().getExpression();
            if (expression.isAssignExpr()) {
                return findFlywayAssignment(statement.asExpressionStmt().getExpression().asAssignExpr());
            }
        }

        if (statement.isExpressionStmt()) {
            final Expression expression = statement.asExpressionStmt().getExpression();
            if (expression.isVariableDeclarationExpr()) {
                return findFlywayDeclaration(expression.asVariableDeclarationExpr());
            }
        }

        return Optional.empty();
    }

    private static Optional<FlywayAnchor> findFlywayDeclaration(final VariableDeclarationExpr declarationExpr) {
        if (declarationExpr.getVariables().size() != 1) {
            return Optional.empty();
        }

        final VariableDeclarator variable = declarationExpr.getVariable(0);
        if (!variable.getInitializer().isPresent() || !isFlywayNoArgConstruction(variable.getInitializer().get())) {
            return Optional.empty();
        }

        return Optional.of(new FlywayAnchor(variable.getNameAsString(), expr -> variable.setInitializer(expr)));
    }

    private static Optional<FlywayAnchor> findFlywayAssignment(final AssignExpr assignExpr) {
        if (!isFlywayNoArgConstruction(assignExpr.getValue())) {
            return Optional.empty();
        }

        final String targetText = assignExpr.getTarget().toString();
        return Optional.of(new FlywayAnchor(targetText, expr -> assignExpr.setValue(expr)));
    }

    private static Optional<SetterInvocation> extractSetter(final Statement statement, final String targetText) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr call = expression.asMethodCallExpr();
        if (!call.getScope().isPresent()) {
            return Optional.empty();
        }

        if (!call.getScope().get().toString().equals(targetText)) {
            return Optional.empty();
        }

        final String methodName = call.getNameAsString();
        if (!isSupportedFlywaySetter(methodName)) {
            return Optional.empty();
        }

        return Optional.of(new SetterInvocation(methodName, call.getArguments()));
    }

    private static boolean isSupportedFlywaySetter(final String methodName) {
        return "setDataSource".equals(methodName)
                || "setClassLoader".equals(methodName)
                || "setLocations".equals(methodName)
                || "setValidateOnMigrate".equals(methodName);
    }

    private static boolean isFlywayNoArgConstruction(final Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        return creationExpr.getType().asString().endsWith("Flyway") && creationExpr.getArguments().isEmpty();
    }

    private static String buildFlywayExpression(final List<SetterInvocation> setters) {
        final Optional<SetterInvocation> classLoaderSetter = setters.stream()
                .filter(setter -> "setClassLoader".equals(setter.methodName) && setter.arguments.size() == 1)
                .findFirst();

        final StringBuilder builder = new StringBuilder("org.flywaydb.core.Flyway.configure(");
        classLoaderSetter.map(setter -> setter.arguments.get(0).toString()).ifPresent(builder::append);
        builder.append(")");

        for (final SetterInvocation setter : setters) {
            if (classLoaderSetter.isPresent() && classLoaderSetter.get() == setter) {
                continue;
            }
            switch (setter.methodName) {
                case "setDataSource":
                    builder.append(".dataSource(").append(joinArgs(setter.arguments)).append(")");
                    break;
                case "setLocations":
                    builder.append(".locations(").append(joinArgs(setter.arguments)).append(")");
                    break;
                case "setValidateOnMigrate":
                    builder.append(".validateOnMigrate(").append(joinArgs(setter.arguments)).append(")");
                    break;
                default:
                    break;
            }
        }

        return builder.append(".load()").toString();
    }

    private static String joinArgs(final NodeList<Expression> arguments) {
        return arguments.stream().map(Expression::toString).collect(Collectors.joining(", "));
    }

    private static final class FlywayAnchor {
        private final String targetText;
        private final InitializerSetter initializerSetter;

        private FlywayAnchor(final String targetText, final InitializerSetter initializerSetter) {
            this.targetText = targetText;
            this.initializerSetter = initializerSetter;
        }

        private void replaceInitializer(final Expression replacement) {
            this.initializerSetter.set(replacement);
        }
    }

    @FunctionalInterface
    private interface InitializerSetter {
        void set(Expression expression);
    }

    private static final class SetterInvocation {
        private final String methodName;
        private final NodeList<Expression> arguments;

        private SetterInvocation(final String methodName, final NodeList<Expression> arguments) {
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }
}
