package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OBJECT_MAPPER = "ObjectMapper";
    private static final String OBJECT_WRITER = "ObjectWriter";
    private static final String FILE = "File";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path");
        }

        Path root = Paths.get(args[0]);
        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean[] changed = {false};

            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (!isJacksonFileWrite(cu, call)) {
                    return;
                }

                ExpressionStmt parentStmt = call.findAncestor(ExpressionStmt.class).orElse(null);
                if (parentStmt == null) {
                    return;
                }

                Expression scope = call.getScope().orElse(null);
                if (scope == null) {
                    return;
                }

                String replacement = String.format(
                        "java.nio.file.Files.write(%s.toPath(), %s.writeValueAsBytes(%s));",
                        call.getArgument(0),
                        scope,
                        call.getArgument(1)
                );

                parentStmt.replace(StaticJavaParser.parseStatement(replacement));
                changed[0] = true;
            });

            if (changed[0]) {
                Files.writeString(path, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isJacksonFileWrite(CompilationUnit cu, MethodCallExpr call) {
        if (!"writeValue".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }

        if (!isFileLike(cu, call.getArgument(0))) {
            return false;
        }

        Expression scope = call.getScope().orElse(null);
        return scope != null && (isMapperScope(cu, scope) || isWriterScope(cu, scope));
    }

    private static boolean isMapperScope(CompilationUnit cu, Expression scope) {
        return isTypeNamed(cu, scope, OBJECT_MAPPER)
                || (scope.isObjectCreationExpr() && scope.asObjectCreationExpr().getType().getNameAsString().equals(OBJECT_MAPPER))
                || (scope.isMethodCallExpr() && ("writer".equals(scope.asMethodCallExpr().getNameAsString())
                || "writerWithDefaultPrettyPrinter".equals(scope.asMethodCallExpr().getNameAsString()))
                && isMapperScope(cu, scope.asMethodCallExpr().getScope().orElse(null)));
    }

    private static boolean isWriterScope(CompilationUnit cu, Expression scope) {
        return isTypeNamed(cu, scope, OBJECT_WRITER)
                || (scope.isMethodCallExpr() && ("writer".equals(scope.asMethodCallExpr().getNameAsString())
                || "writerWithDefaultPrettyPrinter".equals(scope.asMethodCallExpr().getNameAsString()))
                && isMapperScope(cu, scope.asMethodCallExpr().getScope().orElse(null)));
    }

    private static boolean isTypeNamed(CompilationUnit cu, Expression scope, String simpleTypeName) {
        if (scope == null) {
            return false;
        }

        if (scope.isNameExpr()) {
            String variableName = scope.asNameExpr().getNameAsString();
            return cu.findAll(VariableDeclarator.class).stream()
                    .anyMatch(variable -> variable.getNameAsString().equals(variableName)
                            && variable.getType().asString().endsWith(simpleTypeName));
        }

        if (scope.isObjectCreationExpr()) {
            return scope.asObjectCreationExpr().getType().getNameAsString().endsWith(simpleTypeName);
        }

        return scope.toString().endsWith(simpleTypeName);
    }

    private static boolean isFileLike(CompilationUnit cu, Expression expression) {
        if (expression.isObjectCreationExpr()) {
            return expression.asObjectCreationExpr().getType().getNameAsString().endsWith(FILE);
        }

        if (expression.isMethodCallExpr()) {
            return expression.asMethodCallExpr().getNameAsString().equals("createTempFile");
        }

        if (expression.isNameExpr()) {
            String name = expression.asNameExpr().getNameAsString();
            return cu.findAll(VariableDeclarator.class).stream()
                    .anyMatch(variable -> variable.getNameAsString().equals(name)
                            && variable.getType().asString().endsWith(FILE));
        }

        return false;
    }
}
