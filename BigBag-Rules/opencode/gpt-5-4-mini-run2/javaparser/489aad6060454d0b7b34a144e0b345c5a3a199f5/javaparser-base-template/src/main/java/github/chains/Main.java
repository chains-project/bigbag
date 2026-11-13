package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
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
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        final Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        final JavaParser parser = new JavaParser(new ParserConfiguration());

        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> {
                        try {
                            final CompilationUnit cu = parser.parse(path).getResult().orElseThrow(() ->
                                    new IllegalStateException("Failed to parse " + path));
                            LexicalPreservingPrinter.setup(cu);
                            if (new FlywayTransformer().transform(cu)) {
                                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    private static final class FlywayTransformer {
        boolean transform(final CompilationUnit cu) {
            boolean changed = false;
            for (final BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= transformBlock(block);
            }
            return changed;
        }

        private boolean transformBlock(final BlockStmt block) {
            boolean changed = false;
            final NodeList<Statement> statements = block.getStatements();

            for (int i = 0; i < statements.size(); i++) {
                final Statement statement = statements.get(i);
                if (!statement.isExpressionStmt()) {
                    continue;
                }

                final ExpressionStmt exprStmt = statement.asExpressionStmt();
                if (!exprStmt.getExpression().isVariableDeclarationExpr()) {
                    continue;
                }

                final VariableDeclarationExpr declaration = exprStmt.getExpression().asVariableDeclarationExpr();
                if (declaration.getVariables().size() != 1) {
                    continue;
                }

                final VariableDeclarator variable = declaration.getVariable(0);
                if (!isFlywayConstruction(variable)) {
                    continue;
                }

                final String variableName = variable.getNameAsString();
                final List<MethodCallExpr> setters = new ArrayList<>();
                int end = i + 1;
                while (end < statements.size()) {
                    final Optional<MethodCallExpr> setter = asSupportedSetter(statements.get(end), variableName);
                    if (!setter.isPresent()) {
                        break;
                    }
                    setters.add(setter.get());
                    end++;
                }

                if (setters.isEmpty()) {
                    continue;
                }

                variable.setInitializer(buildConfigurationChain(setters));
                for (int removeIndex = end - 1; removeIndex >= i + 1; removeIndex--) {
                    statements.remove(removeIndex);
                }
                changed = true;
            }

            return changed;
        }

        private boolean isFlywayConstruction(final VariableDeclarator variable) {
            if (!variable.getType().asString().endsWith("Flyway")) {
                return false;
            }
            return variable.getInitializer()
                    .filter(Expression::isObjectCreationExpr)
                    .map(Expression::asObjectCreationExpr)
                    .filter(this::isNoArgFlywayCreation)
                    .isPresent();
        }

        private boolean isNoArgFlywayCreation(final ObjectCreationExpr creation) {
            return creation.getType().asString().endsWith("Flyway") && creation.getArguments().isEmpty();
        }

        private Optional<MethodCallExpr> asSupportedSetter(final Statement statement, final String variableName) {
            if (!statement.isExpressionStmt()) {
                return Optional.empty();
            }
            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                return Optional.empty();
            }
            final MethodCallExpr call = expression.asMethodCallExpr();
            if (call.getScope().isEmpty() || !call.getScope().get().isNameExpr()) {
                return Optional.empty();
            }
            if (!call.getScope().get().asNameExpr().getNameAsString().equals(variableName)) {
                return Optional.empty();
            }

            switch (call.getNameAsString()) {
                case "setDataSource":
                case "setClassLoader":
                case "setLocations":
                case "setValidateOnMigrate":
                    return Optional.of(call);
                default:
                    return Optional.empty();
            }
        }

        private Expression buildConfigurationChain(final List<MethodCallExpr> setters) {
            MethodCallExpr current = new MethodCallExpr(new com.github.javaparser.ast.expr.NameExpr("Flyway"), "configure");

            for (final MethodCallExpr setter : setters) {
                final String methodName = mapSetterName(setter.getNameAsString());
                final MethodCallExpr next = new MethodCallExpr(current, methodName);
                next.setArguments(new NodeList<>(setter.getArguments()));
                current = next;
            }

            return new MethodCallExpr(current, "load");
        }

        private String mapSetterName(final String setterName) {
            switch (setterName) {
                case "setDataSource":
                    return "dataSource";
                case "setClassLoader":
                    return "classLoader";
                case "setLocations":
                    return "locations";
                case "setValidateOnMigrate":
                    return "validateOnMigrate";
                default:
                    throw new IllegalArgumentException("Unsupported Flyway setter: " + setterName);
            }
        }
    }
}
