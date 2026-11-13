package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_SIMPLE_NAME = "Flyway";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root path");
        }
        try {
            transform(Paths.get(args[0]));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(final Path sourceRootPath) throws IOException {
        final SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        final List<com.github.javaparser.ParseResult<CompilationUnit>> results = sourceRoot.tryToParseParallelized();
        for (final com.github.javaparser.ParseResult<CompilationUnit> result : results) {
            if (!result.getResult().isPresent()) {
                continue;
            }
            final CompilationUnit compilationUnit = result.getResult().get();
            LexicalPreservingPrinter.setup(compilationUnit);
            rewriteCompilationUnit(compilationUnit);
            final Path filePath = sourceRootPath.resolve(compilationUnit.getStorage().orElseThrow(() ->
                    new IllegalStateException("Missing storage")).getPath());
            Files.write(filePath, LexicalPreservingPrinter.print(compilationUnit).getBytes());
        }
    }

    private static void rewriteCompilationUnit(final CompilationUnit compilationUnit) {
        compilationUnit.findAll(BlockStmt.class).forEach(Main::rewriteBlock);
    }

    private static void rewriteBlock(final BlockStmt blockStmt) {
        final List<Statement> statements = new ArrayList<>(blockStmt.getStatements());
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

            final VariableDeclarator variable = declarationExpr.getVariable(0);
            if (!isFlywayType(variable.getType())) {
                continue;
            }
            if (!variable.getInitializer().isPresent() || !isZeroArgFlywayCreation(variable.getInitializer().get())) {
                continue;
            }

            final String variableName = variable.getNameAsString();
            final List<MethodCallExpr> setters = new ArrayList<>();
            int j = i + 1;
            while (j < statements.size()) {
                final Statement next = statements.get(j);
                if (!next.isExpressionStmt()) {
                    break;
                }
                final MethodCallExpr call = next.asExpressionStmt().getExpression().toMethodCallExpr().orElse(null);
                if (call == null || !isSupportedSetter(call) || !isCallOnVariable(call, variableName)) {
                    break;
                }
                setters.add(call);
                j++;
            }

            if (setters.isEmpty()) {
                continue;
            }

            variable.setInitializer(buildFlywayChain(setters));
            for (int k = i + 1; k < j; k++) {
                statements.get(k).remove();
            }
            i = j - 1;
        }
    }

    private static boolean isZeroArgFlywayCreation(final Expression expression) {
        return expression.isObjectCreationExpr()
                && expression.asObjectCreationExpr().getArguments().isEmpty()
                && isFlywayType(expression.asObjectCreationExpr().getType());
    }

    private static boolean isSupportedSetter(final MethodCallExpr call) {
        final String name = call.getNameAsString();
        return "setDataSource".equals(name)
                || "setClassLoader".equals(name)
                || "setLocations".equals(name)
                || "setValidateOnMigrate".equals(name);
    }

    private static boolean isCallOnVariable(final MethodCallExpr call, final String variableName) {
        return call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static Expression buildFlywayChain(final List<MethodCallExpr> setters) {
        MethodCallExpr classLoaderCall = null;
        final List<MethodCallExpr> orderedSetters = new ArrayList<>();
        for (final MethodCallExpr setter : setters) {
            if ("setClassLoader".equals(setter.getNameAsString())) {
                classLoaderCall = setter;
            } else {
                orderedSetters.add(setter);
            }
        }
        final MethodCallExpr root = classLoaderCall == null
                ? new MethodCallExpr(new NameExpr("Flyway"), "configure")
                : new MethodCallExpr(new NameExpr("Flyway"), "configure", classLoaderCall.getArguments());
        MethodCallExpr configured = root;
        for (final MethodCallExpr setter : orderedSetters) {
            configured = appendSetter(configured, setter);
        }
        return new MethodCallExpr(configured, "load");
    }

    private static MethodCallExpr appendSetter(final MethodCallExpr chain, final MethodCallExpr setter) {
        final NodeList<Expression> args = setter.getArguments();
        switch (setter.getNameAsString()) {
            case "setDataSource":
                return new MethodCallExpr(chain, "dataSource", args);
            case "setLocations":
                return new MethodCallExpr(chain, "locations", args);
            case "setValidateOnMigrate":
                return new MethodCallExpr(chain, "validateOnMigrate", args);
            default:
                return chain;
        }
    }

    private static boolean isFlywayType(final Type type) {
        final String rendered = type.asString();
        return FLYWAY_SIMPLE_NAME.equals(rendered) || rendered.endsWith("." + FLYWAY_SIMPLE_NAME);
    }
}
