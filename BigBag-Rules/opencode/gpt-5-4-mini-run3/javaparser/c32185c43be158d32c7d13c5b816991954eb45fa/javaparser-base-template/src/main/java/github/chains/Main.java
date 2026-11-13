package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
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
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(final Path javaFile) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            final boolean changed = transform(compilationUnit);
            if (changed) {
                Files.write(javaFile, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + javaFile, e);
        }
    }

    private static boolean transform(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (final BlockStmt blockStmt : compilationUnit.findAll(BlockStmt.class)) {
            changed |= transformBlock(blockStmt);
        }
        return changed;
    }

    private static boolean transformBlock(final BlockStmt blockStmt) {
        boolean changed = false;
        final List<Statement> statements = blockStmt.getStatements();
        int index = 0;
        while (index < statements.size()) {
            final Statement statement = statements.get(index);
            final Optional<VariableDeclarator> flywayVariable = getFlywayVariable(statement);
            if (!flywayVariable.isPresent()) {
                index++;
                continue;
            }

            final VariableDeclarator variableDeclarator = flywayVariable.get();
            final String variableName = variableDeclarator.getNameAsString();
            final String declaredType = variableDeclarator.getType().asString();
            final Optional<Expression> initializer = variableDeclarator.getInitializer();
            if (!initializer.isPresent() || !isOldFlywayConstructor(initializer.get())) {
                index++;
                continue;
            }

            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            int nextIndex = index + 1;
            while (nextIndex < statements.size()) {
                final Optional<MethodCallExpr> setterCall = getSetterCall(statements.get(nextIndex), variableName);
                if (!setterCall.isPresent()) {
                    break;
                }
                setterCalls.add(setterCall.get());
                nextIndex++;
            }

            if (setterCalls.isEmpty()) {
                index++;
                continue;
            }

            variableDeclarator.setInitializer(buildReplacementExpression(declaredType, setterCalls));
            for (int removeIndex = nextIndex - 1; removeIndex > index; removeIndex--) {
                statements.remove(removeIndex);
            }

            changed = true;
            index++;
        }
        return changed;
    }

    private static Optional<VariableDeclarator> getFlywayVariable(final Statement statement) {
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

        return Optional.of(declarationExpr.getVariable(0));
    }

    private static boolean isOldFlywayConstructor(final Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
        return objectCreationExpr.getArguments().isEmpty() && objectCreationExpr.getType().asString().endsWith("Flyway");
    }

    private static Optional<MethodCallExpr> getSetterCall(final Statement statement, final String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
        if (!methodCallExpr.getNameAsString().startsWith("set") || methodCallExpr.getArguments().size() == 0) {
            return Optional.empty();
        }

        if (!methodCallExpr.getScope().isPresent()) {
            return Optional.empty();
        }

        final Expression scope = methodCallExpr.getScope().get();
        if (!scope.isNameExpr() || !scope.asNameExpr().getNameAsString().equals(variableName)) {
            return Optional.empty();
        }

        return Optional.of(methodCallExpr);
    }

    private static Expression buildReplacementExpression(final String declaredType, final List<MethodCallExpr> setterCalls) {
        final Optional<MethodCallExpr> classLoaderSetter = setterCalls.stream()
                .filter(call -> call.getNameAsString().equals("setClassLoader") && call.getArguments().size() == 1)
                .findFirst();

        final MethodCallExpr root = StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()").asMethodCallExpr();

        if (classLoaderSetter.isPresent()) {
            root.addArgument(classLoaderSetter.get().getArgument(0).clone());
        }

        MethodCallExpr expression = root;
        for (final MethodCallExpr setterCall : setterCalls) {
            if (setterCall.getNameAsString().equals("setClassLoader")) {
                continue;
            }
            final MethodCallExpr nextCall = new MethodCallExpr(expression, toBuilderMethodName(setterCall.getNameAsString()));
            setterCall.getArguments().forEach(argument -> nextCall.addArgument(argument.clone()));
            expression = nextCall;
        }
        return new MethodCallExpr(expression, "load");
    }

    private static String toBuilderMethodName(final String setterName) {
        final String baseName = setterName.substring(3);
        if (baseName.isEmpty()) {
            return setterName;
        }
        return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
    }
}
