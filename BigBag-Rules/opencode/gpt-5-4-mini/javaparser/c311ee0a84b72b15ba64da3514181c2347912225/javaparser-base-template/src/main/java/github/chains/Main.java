package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "StringContains",
        "StringStartsWith",
        "StringEndsWith"
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteIfNeeded);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to traverse source tree", err);
        }
    }

    private static void rewriteIfNeeded(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;
            for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (isLegacyCaseSensitiveStringMatcher(expr)) {
                    final Expression replacement = replacement(expr);
                    expr.replace(replacement);
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (final Exception err) {
            throw new IllegalStateException("Failed to rewrite " + file, err);
        }
    }

    private static boolean isLegacyCaseSensitiveStringMatcher(final ObjectCreationExpr expr) {
        return TARGET_TYPES.contains(expr.getType().getNameAsString())
            && expr.getArguments().size() == 2
            && expr.getArgument(0).isBooleanLiteralExpr();
    }

    private static Expression replacement(final ObjectCreationExpr expr) {
        final ObjectCreationExpr updated = expr.clone();
        updated.setArguments(NodeList.nodeList(expr.getArgument(1).clone()));
        return updated;
    }
}
