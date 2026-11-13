package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk source tree: " + root, err);
        }
    }

    private static void rewrite(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(unit);
            boolean changed = false;
            for (ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
                final Node replacement = replacement(expr);
                if (replacement != expr) {
                    expr.replace(replacement);
                    changed = true;
                }
            }
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception err) {
            throw new IllegalStateException("Failed to rewrite " + path, err);
        }
    }

    private static Node replacement(final ObjectCreationExpr expr) {
        final String name = expr.getType().getNameAsString();
        if (expr.getArguments().size() != 2 || !expr.getArgument(0).isBooleanLiteralExpr()) {
            return expr;
        }
        if ("StringContains".equals(name)) {
            return parsed(expr.getArgument(1), name);
        }
        if ("StringStartsWith".equals(name)) {
            return parsed(expr.getArgument(1), name);
        }
        return expr;
    }

    private static Expression parsed(final Expression arg, final String type) {
        return StaticJavaParser.parseExpression(String.format("new %s(%s)", type, arg));
    }
}
