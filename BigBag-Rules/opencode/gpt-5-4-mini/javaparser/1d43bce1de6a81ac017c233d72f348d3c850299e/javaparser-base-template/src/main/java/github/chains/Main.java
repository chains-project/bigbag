package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteJavaFile);
    }

    private static void rewriteJavaFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = rewriteFlywayBlocks(cu);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Leave files untouched if parsing fails.
        }
    }

    private static boolean rewriteFlywayBlocks(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= rewriteFlywayBlock(block);
        }
        return changed;
    }

    private static boolean rewriteFlywayBlock(BlockStmt block) {
        List<Statement> statements = new ArrayList<>(block.getStatements());
        Map<String, Integer> declarationIndexes = new LinkedHashMap<>();
        Map<String, VariableDeclarator> flywayVariables = new LinkedHashMap<>();
        Map<String, List<MethodCallExpr>> setterCallsByVariable = new LinkedHashMap<>();

        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
            for (VariableDeclarator variable : declaration.getVariables()) {
                if (isOldFlywayCreation(variable.getInitializer())) {
                    flywayVariables.put(variable.getNameAsString(), variable);
                    declarationIndexes.put(variable.getNameAsString(), i);
                    setterCallsByVariable.put(variable.getNameAsString(), new ArrayList<>());
                }
            }
        }

        if (flywayVariables.isEmpty()) {
            return false;
        }

        List<Statement> statementsToRemove = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                continue;
            }

            MethodCallExpr call = expression.asMethodCallExpr();
            Optional<String> variableName = getScopeName(call);
            if (variableName.isEmpty()) {
                continue;
            }

            String name = variableName.get();
            Integer declarationIndex = declarationIndexes.get(name);
            if (declarationIndex == null || i <= declarationIndex) {
                continue;
            }

            if (!isFlywaySetter(call.getNameAsString())) {
                continue;
            }

            setterCallsByVariable.get(name).add(call);
            statementsToRemove.add(statement);
        }

        boolean changed = false;
        for (Map.Entry<String, VariableDeclarator> entry : flywayVariables.entrySet()) {
            String variableName = entry.getKey();
            VariableDeclarator variable = entry.getValue();
            List<MethodCallExpr> setterCalls = setterCallsByVariable.get(variableName);
            if (setterCalls == null) {
                continue;
            }

            variable.setInitializer(buildReplacementInitializer(setterCalls));
            changed = true;
        }

        for (Statement statement : statementsToRemove) {
            statement.remove();
            changed = true;
        }

        return changed;
    }

    private static boolean isOldFlywayCreation(Optional<Expression> initializer) {
        if (initializer.isEmpty() || !initializer.get().isObjectCreationExpr()) {
            return false;
        }

        ObjectCreationExpr creation = initializer.get().asObjectCreationExpr();
        return creation.getType().asString().equals("Flyway") || creation.getType().asString().equals(FLYWAY_TYPE);
    }

    private static Expression buildReplacementInitializer(List<MethodCallExpr> setterCalls) {
        String classLoaderArgument = null;
        List<MethodCallExpr> fluentCalls = new ArrayList<>();

        for (MethodCallExpr call : setterCalls) {
            String name = call.getNameAsString();
            if ("setClassLoader".equals(name)) {
                classLoaderArgument = call.getArgument(0).toString();
                continue;
            }

            fluentCalls.add(call);
        }

        StringBuilder replacement = new StringBuilder();
        replacement.append(FLYWAY_TYPE).append(".configure(");
        if (classLoaderArgument != null) {
            replacement.append(classLoaderArgument);
        }
        replacement.append(")");

        for (MethodCallExpr call : fluentCalls) {
            replacement.append('.').append(toFluentName(call.getNameAsString())).append('(');
            for (int i = 0; i < call.getArguments().size(); i++) {
                if (i > 0) {
                    replacement.append(", ");
                }
                replacement.append(call.getArgument(i));
            }
            replacement.append(')');
        }

        replacement.append(".load()");
        return StaticJavaParser.parseExpression(replacement.toString());
    }

    private static Optional<String> getScopeName(MethodCallExpr call) {
        return call.getScope()
                .filter(Expression::isNameExpr)
                .map(Expression::asNameExpr)
                .map(NameExpr::getNameAsString);
    }

    private static boolean isFlywaySetter(String methodName) {
        return "setDataSource".equals(methodName)
                || "setLocations".equals(methodName)
                || "setLocationsAsStrings".equals(methodName)
                || "setValidateOnMigrate".equals(methodName)
                || "setClassLoader".equals(methodName);
    }

    private static String toFluentName(String setterName) {
        if (setterName.startsWith("set") && setterName.length() > 3) {
            return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
        }
        return setterName;
    }
}
