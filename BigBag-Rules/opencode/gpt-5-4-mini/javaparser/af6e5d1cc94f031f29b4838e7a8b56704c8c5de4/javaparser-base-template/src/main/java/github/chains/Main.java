package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    private static final Map<String, String> SETTER_TO_FLUENT = Map.of(
            "setDataSource", "dataSource",
            "setClassLoader", "classLoader",
            "setLocations", "locations",
            "setValidateOnMigrate", "validateOnMigrate"
    );

    public static void main(String[] args) {
        final Path sourceRoot = Paths.get(args.length == 0 ? "." : args[0]);
        try {
            transform(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteJavaFile);
        }
    }

    private static void rewriteJavaFile(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            boolean changed = transformFlywayBlocks(cu);
            changed |= transformRemainingFlywayCreations(cu);
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean transformFlywayBlocks(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            final List<Statement> rewritten = new ArrayList<>();
            final List<Statement> statements = block.getStatements();
            for (int i = 0; i < statements.size(); i++) {
                final Statement statement = statements.get(i);
                final Optional<VariableDeclarator> maybeVariable = getFlywayVariable(statement);
                if (!maybeVariable.isPresent()) {
                    rewritten.add(statement);
                    continue;
                }

                final VariableDeclarator variable = maybeVariable.get();
                final String variableName = variable.getNameAsString();
                Expression classLoaderArg = null;
                final List<MethodCallExpr> setterCalls = new ArrayList<>();

                int j = i + 1;
                while (j < statements.size()) {
                    final Optional<MethodCallExpr> maybeSetter = getFlywaySetterCall(statements.get(j), variableName);
                    if (!maybeSetter.isPresent()) {
                        break;
                    }

                    final MethodCallExpr setterCall = maybeSetter.get();
                    if ("setClassLoader".equals(setterCall.getNameAsString())) {
                        classLoaderArg = setterCall.getArguments().get(0);
                    } else {
                        setterCalls.add(setterCall);
                    }
                    j++;
                    changed = true;
                }

                Expression replacement = createFlywayConfigureCall(classLoaderArg);
                for (MethodCallExpr setterCall : setterCalls) {
                    replacement = new MethodCallExpr(replacement, fluentName(setterCall.getNameAsString()), new NodeList<>(setterCall.getArguments()));
                }
                replacement = new MethodCallExpr(replacement, "load");
                variable.setInitializer(replacement);
                rewritten.add(statement);
                changed = true;
                i = j - 1;
            }
            if (changed) {
                block.getStatements().clear();
                block.getStatements().addAll(rewritten);
            }
        }
        return changed;
    }

    private static boolean transformRemainingFlywayCreations(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isFlywayCreation(creation)) {
                continue;
            }
            if (creation.getParentNode().filter(parent -> parent instanceof VariableDeclarator).isPresent()) {
                continue;
            }

            creation.replace(new MethodCallExpr(createFlywayConfigureCall(null), "load"));
            changed = true;
        }
        return changed;
    }

    private static Optional<VariableDeclarator> getFlywayVariable(Statement statement) {
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
        if (!isFlywayType(variable.getType().asString())) {
            return Optional.empty();
        }

        if (!variable.getInitializer().isPresent() || !isFlywayCreation(variable.getInitializer().get())) {
            return Optional.empty();
        }

        return Optional.of(variable);
    }

    private static Optional<MethodCallExpr> getFlywaySetterCall(Statement statement, String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr call = expression.asMethodCallExpr();
        if (!SETTER_TO_FLUENT.containsKey(call.getNameAsString())) {
            return Optional.empty();
        }

        if (!call.getScope().isPresent() || !isTargetReceiver(call.getScope().get(), variableName)) {
            return Optional.empty();
        }

        return Optional.of(call);
    }

    private static boolean isTargetReceiver(Expression scope, String variableName) {
        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString().equals(variableName);
        }
        return scope.toString().endsWith("." + variableName);
    }

    private static boolean isFlywayCreation(Expression expression) {
        return expression.isObjectCreationExpr() && isFlywayCreation(expression.asObjectCreationExpr());
    }

    private static boolean isFlywayCreation(ObjectCreationExpr creation) {
        return isFlywayType(creation.getType().asString()) && creation.getArguments().isEmpty();
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_FQCN.equals(typeName) || "Flyway".equals(typeName) || typeName.endsWith(".Flyway");
    }

    private static Expression createFlywayConfigureCall(Expression classLoaderArg) {
        if (classLoaderArg == null) {
            return StaticJavaParser.parseExpression(FLYWAY_FQCN + ".configure()");
        }
        return new MethodCallExpr(StaticJavaParser.parseExpression(FLYWAY_FQCN), "configure", new NodeList<>(classLoaderArg));
    }

    private static String fluentName(String setterName) {
        return SETTER_TO_FLUENT.getOrDefault(setterName, setterName);
    }

}
