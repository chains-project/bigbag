package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, String> FACTORIES = new HashMap<>();

    static {
        FACTORIES.put("StringContains", "containsString");
        FACTORIES.put("StringStartsWith", "startsWith");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }
        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(String.format("Not a directory: %s", root));
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk source tree", err);
        }
    }

    private static void transform(final Path file) {
        try {
            final String source = Files.readString(file, StandardCharsets.UTF_8);
            final CompilationUnit cu = StaticJavaParser.parse(source);
            boolean changed = false;
            for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                final Expression replaced = rewrite(expr);
                if (replaced != null) {
                    expr.replace(replaced);
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }

    private static Expression rewrite(final ObjectCreationExpr expr) {
        final String name = expr.getType().getNameAsString();
        final String factory = FACTORIES.get(name);
        if (factory == null || expr.getArguments().size() != 2) {
            return null;
        }
        final Expression flag = expr.getArgument(0);
        final Expression text = expr.getArgument(1);
        final Expression ignore = call(factory + "IgnoringCase", text.clone());
        final Expression normal = call(factory, text.clone());
        if (flag instanceof BooleanLiteralExpr) {
            return ((BooleanLiteralExpr) flag).getValue() ? ignore : normal;
        }
        return new ConditionalExpr(flag.clone(), ignore, normal);
    }

    private static MethodCallExpr call(final String name, final Expression argument) {
        return StaticJavaParser.parseExpression(
            String.format("org.hamcrest.Matchers.%s(%s)", name, argument)
        ).asMethodCallExpr();
    }
}
