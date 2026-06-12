package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String LOGBACK_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String LOGBACK_LEVEL = "ch.qos.logback.classic.Level";

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= normalizeLoggerImports(cu);
            changed |= rewriteLoggerCasts(cu);
            changed |= rewriteSetLevelCalls(cu);
            changed |= rewriteLoggerFactories(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean normalizeLoggerImports(CompilationUnit cu) {
        boolean changed = false;

        if (cu.getImports().removeIf(importDeclaration -> !importDeclaration.isAsterisk()
                && LOGBACK_LOGGER.equals(importDeclaration.getNameAsString()))) {
            changed = true;
        }

        if (cu.getImports().stream().noneMatch(importDeclaration -> !importDeclaration.isAsterisk()
                && "org.slf4j.Logger".equals(importDeclaration.getNameAsString()))) {
            cu.addImport("org.slf4j.Logger");
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteLoggerCasts(CompilationUnit cu) {
        boolean changed = false;
        for (CastExpr castExpr : cu.findAll(CastExpr.class)) {
            if (isLogbackLoggerReference(castExpr.getType().toString())) {
                castExpr.getType().replace(StaticJavaParser.parseType("org.slf4j.Logger"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteLoggerFactories(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (isLogbackLoggerReference(creationExpr.getType().toString())) {
                creationExpr.getType().replace(StaticJavaParser.parseType("org.slf4j.Logger"));
                changed = true;
            }
        }
        for (VariableDeclarator variableDeclarator : cu.findAll(VariableDeclarator.class)) {
            if (isLogbackLoggerReference(variableDeclarator.getType().toString())) {
                variableDeclarator.setType(StaticJavaParser.parseType("org.slf4j.Logger"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteSetLevelCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"setLevel".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            Optional<Expression> scope = call.getScope();
            if (scope.isEmpty()) {
                continue;
            }
            Optional<ExpressionStmt> stmt = call.findAncestor(ExpressionStmt.class);
            if (stmt.isEmpty()) {
                continue;
            }
            String levelExpr = renderLevelExpression(call.getArgument(0));
            if (levelExpr == null) {
                continue;
            }
            stmt.get().replace(StaticJavaParser.parseStatement(buildSetLevelReplacement(scope.get().toString(), levelExpr)));
            changed = true;
        }
        return changed;
    }

    private static boolean isLogbackLoggerReference(String text) {
        return LOGBACK_LOGGER.equals(text) || text.endsWith("Logger");
    }

    private static boolean isLogbackLoggerReference(Expression expression) {
        return isLogbackLoggerReference(expression.toString());
    }

    private static String renderLevelExpression(Expression expression) {
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) expression;
            String scope = fieldAccessExpr.getScope().toString();
            if (LOGBACK_LEVEL.equals(scope) || "Level".equals(scope)) {
                return fieldAccessExpr.getNameAsString();
            }
        }
        if (expression.toString().startsWith("ch.qos.logback.classic.Level.")) {
            return expression.toString().substring("ch.qos.logback.classic.Level.".length());
        }
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString();
        }
        return null;
    }

    private static String buildSetLevelReplacement(String loggerExpr, String levelExpr) {
        return "{ try { "
                + "java.lang.Object loggerRef = " + loggerExpr + "; "
                + "java.lang.reflect.Method method = loggerRef.getClass().getMethod(\"setLevel\", java.lang.Class.forName(\"" + LOGBACK_LEVEL + "\")); "
                + "method.invoke(loggerRef, java.lang.Class.forName(\"" + LOGBACK_LEVEL + "\").getField(\"" + levelExpr + "\").get(null)); "
                + "} catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }";
    }
}
