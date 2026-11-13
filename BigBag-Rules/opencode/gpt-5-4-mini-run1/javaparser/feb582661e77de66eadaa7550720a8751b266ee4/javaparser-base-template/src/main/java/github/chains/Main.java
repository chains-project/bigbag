package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);

        boolean changed = false;
        changed |= rewriteStringUtils(cu);
        changed |= rewriteExecutorServiceSingleton(cu);

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteStringUtils(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.equals("liquibase.util.StringUtils")) {
                importDeclaration.setName("liquibase.util.StringUtil");
                changed = true;
            } else if (importDeclaration.isStatic() && name.startsWith("liquibase.util.StringUtils.")) {
                importDeclaration.setName(name.replaceFirst("liquibase\\.util\\.StringUtils", "liquibase.util.StringUtil"));
                changed = true;
            }
        }

        for (MethodCallExpr methodCallExpr : cu.findAll(MethodCallExpr.class)) {
            if (!methodCallExpr.getScope().isPresent()) {
                continue;
            }

            Expression scope = methodCallExpr.getScope().get();
            if (isNamedLike(scope, "StringUtils")) {
                methodCallExpr.setScope(reparseExpression("liquibase.util.StringUtil"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteExecutorServiceSingleton(CompilationUnit cu) {
        boolean changed = false;

        for (MethodCallExpr methodCallExpr : cu.findAll(MethodCallExpr.class)) {
            if (!"getInstance".equals(methodCallExpr.getNameAsString()) || !methodCallExpr.getArguments().isEmpty()) {
                continue;
            }

            if (!methodCallExpr.getScope().isPresent()) {
                continue;
            }

            Expression scope = methodCallExpr.getScope().get();
            if (!isNamedLike(scope, "ExecutorService")) {
                continue;
            }

            methodCallExpr.replace(reparseExpression("liquibase.Scope.getCurrentScope().getSingleton(liquibase.executor.ExecutorService.class)"));
            changed = true;
        }

        return changed;
    }

    private static boolean isNamedLike(Expression expression, String simpleName) {
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString().equals(simpleName);
        }

        if (expression instanceof FieldAccessExpr) {
            return expression.toString().endsWith(simpleName);
        }

        return expression.toString().endsWith(simpleName);
    }

    private static Expression reparseExpression(String expression) {
        return StaticJavaParser.parseExpression(expression);
    }
}
