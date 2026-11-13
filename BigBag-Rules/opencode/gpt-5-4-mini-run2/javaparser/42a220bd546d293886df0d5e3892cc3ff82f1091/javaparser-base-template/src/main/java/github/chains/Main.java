package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_TYPE = "com.maxmind.geoip2.record.Location";
    private static final int OLD_ARITY = 5;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (isTargetLocationCreation(cu, expr) && expr.getArguments().size() == OLD_ARITY) {
                    expr.setArguments(adaptLocationArguments(expr.getArguments()));
                    changed = true;
                }
            }

            for (ExplicitConstructorInvocationStmt stmt : cu.findAll(ExplicitConstructorInvocationStmt.class)) {
                if (stmt.isThis()) {
                    continue;
                }
                if (isTargetLocationSubclass(cu, stmt) && stmt.getArguments().size() == OLD_ARITY) {
                    stmt.setArguments(adaptLocationArguments(stmt.getArguments()));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, cu.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isTargetLocationCreation(CompilationUnit cu, ObjectCreationExpr expr) {
        String typeName = expr.getType().asString();
        return TARGET_TYPE.equals(typeName)
                || ("Location".equals(expr.getType().getNameAsString()) && importsTargetType(cu));
    }

    private static boolean isTargetLocationSubclass(CompilationUnit cu, ExplicitConstructorInvocationStmt stmt) {
        return stmt.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(clazz -> clazz.getExtendedTypes().stream().anyMatch(type ->
                        TARGET_TYPE.equals(type.asString())
                                || ("Location".equals(type.getNameAsString()) && importsTargetType(cu))
                ))
                .orElse(false);
    }

    private static boolean importsTargetType(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDecl -> !importDecl.isAsterisk()
                && TARGET_TYPE.equals(importDecl.getNameAsString()));
    }

    private static NodeList<Expression> adaptLocationArguments(NodeList<Expression> original) {
        NodeList<Expression> rewritten = new NodeList<>();
        rewritten.add(original.get(0));
        rewritten.add(new NullLiteralExpr());
        rewritten.add(original.get(1));
        rewritten.add(original.get(2));
        rewritten.add(original.get(3));
        rewritten.add(new NullLiteralExpr());
        rewritten.add(original.get(4));
        return rewritten;
    }
}
