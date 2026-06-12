package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        final List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }

        javaFiles.sort(Comparator.naturalOrder());
        for (Path file : javaFiles) {
            rewriteFile(file);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        final String original = Files.readString(file, StandardCharsets.UTF_8);
        final CompilationUnit cu = StaticJavaParser.parse(original);

        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= rewriteBlock(block);
        }

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteBlock(BlockStmt block) {
        final List<Statement> statements = new ArrayList<>(block.getStatements());
        boolean changed = false;

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!(statement instanceof ExpressionStmt)) {
                continue;
            }

            final ExpressionStmt expressionStmt = (ExpressionStmt) statement;
            if (!expressionStmt.getExpression().isVariableDeclarationExpr()) {
                continue;
            }

            final VariableDeclarationExpr declaration = expressionStmt.getExpression().asVariableDeclarationExpr();
            for (VariableDeclarator variable : declaration.getVariables()) {
                if (variable.getInitializer().isEmpty() || !isFlywayCreation(variable.getInitializer().get())) {
                    continue;
                }

                final List<MethodCallExpr> setters = collectSetters(statements, variable.getNameAsString(), i);
                if (setters.isEmpty()) {
                    continue;
                }

                variable.setInitializer(buildConfigurationChain(setters));
                removeSetterStatements(setters);
                changed = true;
            }
        }

        return changed;
    }

    private static List<MethodCallExpr> collectSetters(List<Statement> statements, String variableName, int declarationIndex) {
        final List<MethodCallExpr> setters = new ArrayList<>();
        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!(statement instanceof ExpressionStmt)) {
                continue;
            }

            final Optional<MethodCallExpr> callOpt = ((ExpressionStmt) statement).getExpression().toMethodCallExpr();
            if (callOpt.isEmpty()) {
                continue;
            }

            final MethodCallExpr call = callOpt.get();
            final Optional<String> scope = scopeName(call.getScope().orElse(null));
            if (scope.isEmpty() || !scope.get().equals(variableName) || !isFlywaySetter(call.getNameAsString())) {
                continue;
            }

            setters.add(call);
        }

        return setters;
    }

    private static void removeSetterStatements(List<MethodCallExpr> setters) {
        final List<Statement> toRemove = new ArrayList<>();
        for (MethodCallExpr setter : setters) {
            setter.findAncestor(Statement.class).ifPresent(toRemove::add);
        }
        toRemove.forEach(Node::remove);
    }

    private static Expression buildConfigurationChain(List<MethodCallExpr> setters) {
        MethodCallExpr classLoaderSetter = null;
        final List<MethodCallExpr> regularSetters = new ArrayList<>();
        for (MethodCallExpr setter : setters) {
            if ("setClassLoader".equals(setter.getNameAsString())) {
                classLoaderSetter = setter;
            } else {
                regularSetters.add(setter);
            }
        }

        Expression chain;
        if (classLoaderSetter == null) {
            chain = StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()");
        } else {
            final NodeList<Expression> args = new NodeList<>();
            classLoaderSetter.getArguments().forEach(argument -> args.add(argument.clone()));
            chain = new MethodCallExpr(StaticJavaParser.parseExpression("org.flywaydb.core.Flyway"), "configure", args);
        }

        for (MethodCallExpr setter : regularSetters) {
            chain = new MethodCallExpr(chain, toFluentName(setter.getNameAsString()), cloneArguments(setter));
        }

        return new MethodCallExpr(chain, "load");
    }

    private static NodeList<Expression> cloneArguments(MethodCallExpr methodCall) {
        final NodeList<Expression> arguments = new NodeList<>();
        methodCall.getArguments().forEach(argument -> arguments.add(argument.clone()));
        return arguments;
    }

    private static Optional<String> scopeName(Expression scope) {
        if (scope == null) {
            return Optional.empty();
        }

        if (scope.isNameExpr()) {
            return Optional.of(scope.asNameExpr().getNameAsString());
        }

        if (scope.isFieldAccessExpr()) {
            final FieldAccessExpr fieldAccessExpr = scope.asFieldAccessExpr();
            if (fieldAccessExpr.getScope().isThisExpr()) {
                return Optional.of(fieldAccessExpr.getNameAsString());
            }
        }

        return Optional.empty();
    }

    private static boolean isFlywayCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creation = expression.asObjectCreationExpr();
        final String typeName = creation.getType().asString();
        return "Flyway".equals(typeName) || FLYWAY_TYPE.equals(typeName) || typeName.endsWith(".Flyway");
    }

    private static boolean isFlywaySetter(String methodName) {
        return methodName.startsWith("set") && methodName.length() > 3;
    }

    private static String toFluentName(String setterName) {
        final String base = setterName.substring(3);
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }
}
