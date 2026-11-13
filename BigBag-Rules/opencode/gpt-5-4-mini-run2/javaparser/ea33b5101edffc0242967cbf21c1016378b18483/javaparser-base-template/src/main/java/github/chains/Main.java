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
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";
    private static final String SET_DATA_SOURCE = "setDataSource";
    private static final String SET_LOCATIONS = "setLocations";
    private static final String SET_VALIDATE_ON_MIGRATE = "setValidateOnMigrate";
    private static final String SET_CLASS_LOADER = "setClassLoader";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            if (transformCompilationUnit(cu)) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        final List<Statement> statements = new ArrayList<>(block.getStatements());
        final List<Statement> statementsToRemove = new ArrayList<>();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            final Optional<VariableDeclarationExpr> maybeDeclaration = asSingleVariableDeclaration(statement);
            if (!maybeDeclaration.isPresent()) {
                continue;
            }

            final VariableDeclarator variable = maybeDeclaration.get().getVariable(0);
            if (!isFlywayNoArgConstruction(variable.getInitializer())) {
                continue;
            }

            final String variableName = variable.getNameAsString();
            final List<SetterCall> setterCalls = new ArrayList<>();
            for (int j = i + 1; j < statements.size(); j++) {
                final Statement candidate = statements.get(j);
                final Optional<SetterCall> maybeSetterCall = toSetterCall(candidate, variableName);
                if (maybeSetterCall.isPresent()) {
                    setterCalls.add(maybeSetterCall.get());
                    statementsToRemove.add(candidate);
                    continue;
                }

                if (!setterCalls.isEmpty()) {
                    break;
                }
            }

            variable.setInitializer(buildFluentConfiguration(setterCalls));
            changed = true;
        }

        if (!statementsToRemove.isEmpty()) {
            statementsToRemove.forEach(block.getStatements()::remove);
        }

        return changed;
    }

    private static Optional<VariableDeclarationExpr> asSingleVariableDeclaration(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        final VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
        return declaration.getVariables().size() == 1 ? Optional.of(declaration) : Optional.empty();
    }

    private static boolean isFlywayNoArgConstruction(Optional<Expression> initializer) {
        if (!initializer.isPresent() || !initializer.get().isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creation = initializer.get().asObjectCreationExpr();
        return creation.getType().asString().endsWith("Flyway") && creation.getArguments().isEmpty();
    }

    private static Optional<SetterCall> toSetterCall(Statement statement, String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr call = expression.asMethodCallExpr();
        if (!call.getScope().isPresent() || !call.getScope().get().isNameExpr()) {
            return Optional.empty();
        }

        if (!call.getScope().get().asNameExpr().getNameAsString().equals(variableName)) {
            return Optional.empty();
        }

        final String methodName = call.getNameAsString();
        if (!isSupportedSetter(methodName)) {
            return Optional.empty();
        }

        return Optional.of(new SetterCall(methodName, call.getArguments().isEmpty() ? null : call.getArgument(0)));
    }

    private static boolean isSupportedSetter(String methodName) {
        return SET_DATA_SOURCE.equals(methodName)
            || SET_LOCATIONS.equals(methodName)
            || SET_VALIDATE_ON_MIGRATE.equals(methodName)
            || SET_CLASS_LOADER.equals(methodName);
    }

    private static Expression buildFluentConfiguration(List<SetterCall> setterCalls) {
        Expression chain = null;
        Expression classLoaderArgument = null;

        for (SetterCall setterCall : setterCalls) {
            if (SET_CLASS_LOADER.equals(setterCall.methodName)) {
                classLoaderArgument = setterCall.argument;
                break;
            }
        }

        if (classLoaderArgument == null) {
            chain = StaticJavaParser.parseExpression(FLYWAY_FQN + ".configure()");
        } else {
            chain = StaticJavaParser.parseExpression(FLYWAY_FQN + ".configure(" + classLoaderArgument + ")");
        }

        for (SetterCall setterCall : setterCalls) {
            if (SET_CLASS_LOADER.equals(setterCall.methodName)) {
                continue;
            }

            chain = new MethodCallExpr(chain, toFluentMethodName(setterCall.methodName), NodeList.nodeList(setterCall.argument));
        }

        return new MethodCallExpr(chain, "load");
    }

    private static String toFluentMethodName(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }

        final String suffix = setterName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static final class SetterCall {
        private final String methodName;
        private final Expression argument;

        private SetterCall(String methodName, Expression argument) {
            this.methodName = methodName;
            this.argument = argument;
        }
    }
}
