package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE = "Flyway";
    private static final Set<String> SETTER_METHODS = new LinkedHashSet<>(Arrays.asList(
            "setDataSource",
            "setClassLoader",
            "setLocations",
            "setValidateOnMigrate"
    ));

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        final List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            transformFile(file);
        }
    }

    private static void transformFile(Path file) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        final boolean changed = new FlywayMigrationTransformer().transform(cu);
        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static final class FlywayMigrationTransformer {
        boolean transform(CompilationUnit cu) {
            boolean changed = false;

            final List<VariableDeclarator> variables = cu.findAll(VariableDeclarator.class);
            final Map<VariableDeclarator, FlywayState> states = new LinkedHashMap<>();

            for (VariableDeclarator variable : variables) {
                if (isOldFlywayInstantiation(variable)) {
                    states.put(variable, new FlywayState(variable));
                }
            }

            if (states.isEmpty()) {
                return false;
            }

            final List<ExpressionStmt> setterStatements = cu.findAll(ExpressionStmt.class);
            for (ExpressionStmt statement : setterStatements) {
                final Expression expression = statement.getExpression();
                if (!expression.isMethodCallExpr()) {
                    continue;
                }

                final MethodCallExpr call = expression.asMethodCallExpr();
                if (!SETTER_METHODS.contains(call.getNameAsString()) || call.getArguments().isEmpty()) {
                    continue;
                }

                final String scopeName = call.getScope()
                        .filter(NameExpr.class::isInstance)
                        .map(NameExpr.class::cast)
                        .map(NameExpr::getNameAsString)
                        .orElse(null);
                if (scopeName == null) {
                    continue;
                }

                final VariableDeclarator target = resolveTargetVariable(states.keySet(), scopeName, call);
                if (target == null) {
                    continue;
                }

                states.get(target).record(call);
                changed = true;
            }

            for (FlywayState state : states.values()) {
                if (state.rewrite()) {
                    changed = true;
                }
            }

            return changed;
        }

        private boolean isOldFlywayInstantiation(VariableDeclarator variable) {
            if (variable.getInitializer().isEmpty() || !isFlywayType(variable.getType().asString())) {
                return false;
            }

            final Expression initializer = variable.getInitializer().get();
            return initializer.isObjectCreationExpr()
                    && isFlywayType(initializer.asObjectCreationExpr().getType().asString())
                    && initializer.asObjectCreationExpr().getArguments().isEmpty();
        }

        private boolean isFlywayType(String typeName) {
            return FLYWAY_SIMPLE.equals(typeName) || FLYWAY_FQCN.equals(typeName);
        }

        private VariableDeclarator resolveTargetVariable(Set<VariableDeclarator> candidates,
                                                         String name,
                                                         MethodCallExpr call) {
            for (VariableDeclarator variable : candidates) {
                if (!variable.getNameAsString().equals(name)) {
                    continue;
                }
                return variable;
            }
            return null;
        }
    }

    private static final class FlywayState {
        private final VariableDeclarator variable;
        private Expression dataSource;
        private Expression classLoader;
        private Expression locations;
        private Expression validateOnMigrate;

        private FlywayState(VariableDeclarator variable) {
            this.variable = variable;
        }

        private void record(MethodCallExpr call) {
            final Expression arg = call.getArgument(0);
            switch (call.getNameAsString()) {
                case "setDataSource":
                    this.dataSource = arg;
                    break;
                case "setClassLoader":
                    this.classLoader = arg;
                    break;
                case "setLocations":
                    this.locations = arg;
                    break;
                case "setValidateOnMigrate":
                    this.validateOnMigrate = arg;
                    break;
                default:
                    break;
            }
        }

        private boolean rewrite() {
            if (this.variable.getInitializer().isEmpty() || !this.variable.getInitializer().get().isObjectCreationExpr()) {
                return false;
            }

            final ObjectCreationExpr creation = this.variable.getInitializer().get().asObjectCreationExpr();
            if (!creation.getType().asString().equals(FLYWAY_SIMPLE) || !creation.getArguments().isEmpty()) {
                return false;
            }

            Expression expression = this.classLoader == null
                    ? StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()")
                    : StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure(" + this.classLoader + ")");

            if (this.dataSource != null) {
                expression = new MethodCallExpr(expression, "dataSource").addArgument(this.dataSource.clone());
            }
            if (this.locations != null) {
                expression = new MethodCallExpr(expression, "locations").addArgument(this.locations.clone());
            }
            if (this.validateOnMigrate != null) {
                expression = new MethodCallExpr(expression, "validateOnMigrate").addArgument(this.validateOnMigrate.clone());
            }

            expression = new MethodCallExpr(expression, "load");
            this.variable.setInitializer(expression);

            this.variable.findAncestor(Statement.class)
                    .filter(ExpressionStmt.class::isInstance)
                    .map(ExpressionStmt.class::cast)
                    .ifPresent(stmt -> removeFlywaySetterStatements(stmt.getParentNode().orElse(null), this.variable.getNameAsString()));
            return true;
        }

        private void removeFlywaySetterStatements(com.github.javaparser.ast.Node scope, String variableName) {
            if (scope == null) {
                return;
            }
            for (ExpressionStmt stmt : scope.findAll(ExpressionStmt.class)) {
                if (!stmt.getExpression().isMethodCallExpr()) {
                    continue;
                }
                final MethodCallExpr call = stmt.getExpression().asMethodCallExpr();
                if (!SETTER_METHODS.contains(call.getNameAsString())) {
                    continue;
                }
                final boolean matches = call.getScope()
                        .filter(NameExpr.class::isInstance)
                        .map(NameExpr.class::cast)
                        .map(NameExpr::getNameAsString)
                        .map(variableName::equals)
                        .orElse(false);
                if (matches) {
                    stmt.remove();
                }
            }
        }
    }
}
