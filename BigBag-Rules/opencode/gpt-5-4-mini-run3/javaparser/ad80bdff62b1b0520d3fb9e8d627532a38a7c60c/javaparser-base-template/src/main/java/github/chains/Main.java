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
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= transformBlock(block);
            }

            if (changed) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        final NodeList<Statement> statements = block.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            final VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
            if (declaration.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator variable = declaration.getVariable(0);
            if (!variable.getInitializer().isPresent() || !isLegacyFlywayConstruction(variable.getInitializer().get())) {
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

                final Expression nextExpression = next.asExpressionStmt().getExpression();
                if (!nextExpression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr methodCall = nextExpression.asMethodCallExpr();
                if (!isCallOnVariable(methodCall, variableName) || !isLegacyFlywaySetter(methodCall)) {
                    break;
                }

                setters.add(methodCall);
                j++;
            }

            if (setters.isEmpty()) {
                continue;
            }

            final String replacement = buildReplacement(variable.getType().toString(), variableName, setters);
            statements.set(i, StaticJavaParser.parseStatement(replacement));

            for (int remove = j - 1; remove > i; remove--) {
                statements.remove(remove);
            }

            changed = true;
        }

        return changed;
    }

    private static boolean isLegacyFlywayConstruction(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creation = expression.asObjectCreationExpr();
        return creation.getArguments().isEmpty() && isFlywayType(creation.getType().toString());
    }

    private static boolean isCallOnVariable(MethodCallExpr methodCall, String variableName) {
        return methodCall.getScope().isPresent()
                && methodCall.getScope().get().isNameExpr()
                && methodCall.getScope().get().asNameExpr().getNameAsString().equals(variableName);
    }

    private static boolean isLegacyFlywaySetter(MethodCallExpr methodCall) {
        final String name = methodCall.getNameAsString();
        return "setDataSource".equals(name)
                || "setClassLoader".equals(name)
                || "setLocations".equals(name)
                || "setValidateOnMigrate".equals(name);
    }

    private static boolean isFlywayType(String typeText) {
        return "Flyway".equals(typeText) || typeText.endsWith(".Flyway");
    }

    private static String buildReplacement(String typeText, String variableName, List<MethodCallExpr> setters) {
        String classLoaderArgument = null;
        final List<MethodCallExpr> fluentSetters = new ArrayList<>();

        for (MethodCallExpr setter : setters) {
            if ("setClassLoader".equals(setter.getNameAsString())) {
                classLoaderArgument = setter.getArgument(0).toString();
            } else {
                fluentSetters.add(setter);
            }
        }

        final StringBuilder expression = new StringBuilder();
        if (classLoaderArgument != null) {
            expression.append("Flyway.configure(").append(classLoaderArgument).append(")");
        } else {
            expression.append("Flyway.configure()");
        }

        for (MethodCallExpr setter : fluentSetters) {
            expression.append('.').append(toFluentName(setter.getNameAsString()));
            expression.append('(');
            for (int i = 0; i < setter.getArguments().size(); i++) {
                if (i > 0) {
                    expression.append(", ");
                }
                expression.append(setter.getArgument(i));
            }
            expression.append(')');
        }

        expression.append(".load()");
        return typeText + " " + variableName + " = " + expression + ";";
    }

    private static String toFluentName(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }

        final String tail = setterName.substring(3);
        return Character.toLowerCase(tail.charAt(0)) + tail.substring(1);
    }
}
