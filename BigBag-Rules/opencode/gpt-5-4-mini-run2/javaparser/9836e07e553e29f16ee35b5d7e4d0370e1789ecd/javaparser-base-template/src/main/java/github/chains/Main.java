package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(final String[] args) throws IOException {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            boolean changed = false;
            for (ObjectCreationExpr expr : List.copyOf(unit.findAll(ObjectCreationExpr.class))) {
                changed |= transform(expr);
            }
            if (changed) {
                Files.writeString(file, unit.toString());
            }
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to process %s", file), err);
        }
    }

    private static boolean transform(final ObjectCreationExpr expr) {
        final String type = expr.getType().getNameAsString();
        if (expr.getArguments().size() != 2) {
            return false;
        }
        if (!"StringContains".equals(type) && !"StringStartsWith".equals(type)) {
            return false;
        }
        final Expression flag = expr.getArgument(0);
        final Expression text = expr.getArgument(1);
        expr.replace(replacement(type, flag, text));
        return true;
    }

    private static Expression replacement(
        final String type,
        final Expression flag,
        final Expression text
    ) {
        final String caseSensitive;
        final String caseInsensitive;
        if ("StringContains".equals(type)) {
            caseSensitive = "containsString";
            caseInsensitive = "containsStringIgnoringCase";
        } else {
            caseSensitive = "startsWith";
            caseInsensitive = "startsWithIgnoringCase";
        }
        if (flag instanceof BooleanLiteralExpr) {
        return StaticJavaParser.parseExpression(
            String.format(
                "org.hamcrest.Matchers.%s(%s)",
                ((BooleanLiteralExpr) flag).getValue() ? caseInsensitive : caseSensitive,
                text
            )
        );
        }
        return StaticJavaParser.parseExpression(
            String.format(
                "%s ? org.hamcrest.Matchers.%s(%s) : org.hamcrest.Matchers.%s(%s)",
                flag,
                caseInsensitive,
                text,
                caseSensitive,
                text
            )
        );
    }
}
