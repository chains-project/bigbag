package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            final String original = Files.readString(file, StandardCharsets.UTF_8);
            final CompilationUnit cu = StaticJavaParser.parse(original);
            if (transformCompilationUnit(cu)) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform " + file, e);
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
        final NodeList<Statement> statements = block.getStatements();
        final List<Statement> toRemove = new ArrayList<>();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!(statement instanceof ExpressionStmt)) {
                continue;
            }

            final Expression expression = ((ExpressionStmt) statement).getExpression();
            if (!(expression instanceof VariableDeclarationExpr)) {
                continue;
            }

            final VariableDeclarationExpr declarationExpr = (VariableDeclarationExpr) expression;
            if (declarationExpr.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator variable = declarationExpr.getVariable(0);
            if (!isFlywayType(variable.getType().asString())) {
                continue;
            }

            if (!isNoArgFlywayConstruction(variable.getInitializer().orElse(null))) {
                continue;
            }

            final String variableName = variable.getNameAsString();
            final List<MethodCallExpr> calls = new ArrayList<>();
            Expression classLoaderExpr = null;
            int j = i + 1;
            while (j < statements.size()) {
                final Statement next = statements.get(j);
                if (!(next instanceof ExpressionStmt)) {
                    break;
                }

                final Expression nextExpression = ((ExpressionStmt) next).getExpression();
                if (!(nextExpression instanceof MethodCallExpr)) {
                    break;
                }

                final MethodCallExpr call = (MethodCallExpr) nextExpression;
                if (!isCallOnVariable(call, variableName)) {
                    break;
                }

                final String methodName = call.getNameAsString();
                if ("setClassLoader".equals(methodName)) {
                    classLoaderExpr = call.getArgument(0);
                    toRemove.add(next);
                } else if (methodName.startsWith("set") && call.getArguments().size() > 0) {
                    calls.add(call);
                    toRemove.add(next);
                } else {
                    break;
                }

                j++;
            }

            if (toRemove.isEmpty()) {
                continue;
            }

            variable.setInitializer(buildFlywayChain(classLoaderExpr, calls));
            changed = true;
            i = j - 1;
        }

        if (changed) {
            statements.removeAll(toRemove);
        }
        return changed;
    }

    private static Expression buildFlywayChain(Expression classLoaderExpr, List<MethodCallExpr> calls) {
        Expression expression = classLoaderExpr == null
                ? parseExpression("org.flywaydb.core.Flyway.configure()")
                : new MethodCallExpr(new NameExpr("org.flywaydb.core.Flyway"), "configure", new NodeList<>(classLoaderExpr.clone()));

        for (MethodCallExpr call : calls) {
            expression = new MethodCallExpr(expression, toFluentMethodName(call.getNameAsString()), cloneArguments(call));
        }

        return new MethodCallExpr(expression, "load");
    }

    private static NodeList<Expression> cloneArguments(MethodCallExpr call) {
        final NodeList<Expression> cloned = new NodeList<>();
        for (Expression arg : call.getArguments()) {
            cloned.add(arg.clone());
        }
        return cloned;
    }

    private static Expression parseExpression(String text) {
        return StaticJavaParser.parseExpression(text);
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().filter(scope -> scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(variableName)).isPresent();
    }

    private static boolean isNoArgFlywayConstruction(Expression expression) {
        if (!(expression instanceof ObjectCreationExpr)) {
            return false;
        }

        final ObjectCreationExpr creation = (ObjectCreationExpr) expression;
        return creation.getArguments().isEmpty() && isFlywayType(creation.getType().asString());
    }

    private static boolean isFlywayType(String typeName) {
        return "Flyway".equals(typeName) || FLYWAY_FQCN.endsWith("." + typeName) || typeName.endsWith("." + "Flyway");
    }

    private static String toFluentMethodName(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }

        final String tail = setterName.substring(3);
        return Character.toLowerCase(tail.charAt(0)) + tail.substring(1);
    }
}
