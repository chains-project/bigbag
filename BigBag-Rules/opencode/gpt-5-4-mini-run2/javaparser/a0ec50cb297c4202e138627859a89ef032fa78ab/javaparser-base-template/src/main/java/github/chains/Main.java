package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class Main {

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformSafely);
        }
    }

    private static void transformSafely(final Path file) {
        try {
            transform(file);
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }

    private static void transform(final Path file) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(file);
        final boolean[] changed = {false};
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            final Optional<Expression> maybeScope = call.getScope();
            if (call.getNameAsString().equals("asString") && maybeScope.isPresent()) {
                final Expression scope = maybeScope.get();
                if (scope.isObjectCreationExpr()) {
                    final ObjectCreationExpr hexof = scope.asObjectCreationExpr();
                    if (isType(hexof, "HexOf") && hexof.getArguments().size() == 1) {
                        final Expression inner = hexof.getArgument(0);
                        if (inner.isObjectCreationExpr()) {
                            final ObjectCreationExpr bytesof = inner.asObjectCreationExpr();
                            if (isType(bytesof, "BytesOf") && bytesof.getArguments().size() == 1) {
                                final Expression arg = bytesof.getArgument(0).clone();
                                call.replace(
                                    StaticJavaParser.parseExpression(
                                        String.format(
                                            "org.apache.commons.codec.binary.Hex.encodeHexString(%s)",
                                            arg
                                        )
                                    )
                                );
                                changed[0] = true;
                            }
                        }
                    }
                }
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (isType(expr, "ListOf")) {
                expr.replace(
                    StaticJavaParser.parseExpression(
                        String.format("java.util.List.of(%s)", joinArguments(expr.getArguments()))
                    )
                );
                changed[0] = true;
            }
        });
        if (changed[0]) {
            removeImport(cu, "org.cactoos.io.BytesOf");
            removeImport(cu, "org.cactoos.text.HexOf");
            removeImport(cu, "org.cactoos.list.ListOf");
            Files.writeString(file, cu.toString());
        }
    }

    private static String joinArguments(final List<Expression> arguments) {
        return arguments.stream().map(Expression::toString).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static boolean isType(final ObjectCreationExpr expr, final String simple) {
        return expr.getType().getNameAsString().equals(simple)
            || expr.getType().asString().equals(simple)
            || expr.getType().getNameWithScope().equals(simple);
    }

    private static void removeImport(final CompilationUnit cu, final String name) {
        cu.getImports().removeIf(entry -> !entry.isAsterisk() && entry.getNameAsString().equals(name));
    }
}
