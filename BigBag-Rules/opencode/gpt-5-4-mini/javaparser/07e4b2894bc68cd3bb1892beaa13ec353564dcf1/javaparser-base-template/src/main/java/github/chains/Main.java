package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String SIMPLE_NAME = "ScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transform);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(Path file) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(file);
            Map<String, Expression> extractedResults = new HashMap<>();

            for (VariableDeclarator declarator : unit.findAll(VariableDeclarator.class)) {
                if (!isOldScriptResultType(declarator.getType()) || declarator.getInitializer().isEmpty()) {
                    continue;
                }

                Optional<Expression> extracted = extractWrappedResult(declarator.getInitializer().get());
                if (extracted.isPresent()) {
                    extractedResults.put(declarator.getNameAsString(), extracted.get());
                    declarator.setType("Object");
                    declarator.setInitializer(extracted.get().clone());
                }
            }

            for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                if (!"getJavaScriptResult".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                    continue;
                }

                Expression scope = call.getScope().get();
                if (scope instanceof ObjectCreationExpr) {
                    extractWrappedResult(scope).ifPresent(expr -> call.replace(expr.clone()));
                    continue;
                }

                if (scope instanceof MethodCallExpr) {
                    continue;
                }

                Optional.ofNullable(extractedResults.get(scope.toString()))
                        .ifPresent(expr -> call.replace(expr.clone()));
            }

            unit.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_FQCN));
            Files.write(file, unit.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform " + file, exception);
        }
    }

    private static boolean isOldScriptResultType(Type type) {
        return OLD_FQCN.equals(type.asString()) || SIMPLE_NAME.equals(type.asString());
    }

    private static Optional<Expression> extractWrappedResult(Expression expression) {
        if (expression instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) expression;
            if (isOldScriptResultType(creation.getType()) && creation.getArguments().size() == 1) {
                return Optional.of(creation.getArgument(0));
            }
        }
        return Optional.empty();
    }
}
