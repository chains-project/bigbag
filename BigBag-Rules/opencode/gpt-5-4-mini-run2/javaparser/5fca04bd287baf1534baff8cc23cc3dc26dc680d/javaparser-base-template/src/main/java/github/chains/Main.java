package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class Main {

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        transformSourceTree(sourceRoot);
    }

    private static void transformSourceTree(final Path sourceRoot) throws IOException {
        Files.walkFileTree(
            sourceRoot,
            new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    throws IOException {
                    if (file.toString().endsWith(".java")) {
                        transformFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            }
        );
    }

    private static void transformFile(final Path file) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(file);
        boolean changed = false;
        for (final MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (shouldWrapHeaders(call)) {
                final Expression original = call.getArgument(1);
                final ObjectCreationExpr wrapped = new ObjectCreationExpr(
                    null,
                    StaticJavaParser.parseClassOrInterfaceType("com.artipie.http.Headers.From"),
                    NodeList.nodeList(original.clone())
                );
                call.setArgument(1, wrapped);
                changed = true;
            }
        }
        if (changed) {
            Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean shouldWrapHeaders(final MethodCallExpr call) {
        if (!"accept".equals(call.getNameAsString()) || call.getArguments().size() != 3) {
            return false;
        }
        return !isHeadersFrom(call.getArgument(1));
    }

    private static boolean isHeadersFrom(final Expression expression) {
        return expression.isObjectCreationExpr()
            && expression.asObjectCreationExpr().getType().toString().endsWith("Headers.From");
    }
}
