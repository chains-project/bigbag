package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    );

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected source root path");
        }
        final Path root = Paths.get(args[0]);
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewrite);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final String source = Files.readString(file);
            final CompilationUnit unit = StaticJavaParser.parse(source);
            boolean changed = false;
            for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
                if (shouldRewrite(expr)) {
                    expr.replace(StaticJavaParser.parseExpression(replacement(expr)));
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
            }
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to rewrite %s", file), err);
        }
    }

    private static boolean shouldRewrite(final ObjectCreationExpr expr) {
        final String type = expr.getType().asString();
        return expr.getArguments().size() == 2
            && expr.getArgument(0) instanceof BooleanLiteralExpr
            && (TARGET_TYPES.contains(type) || TARGET_TYPES.stream().anyMatch(type::endsWith));
    }

    private static String replacement(final ObjectCreationExpr expr) {
        final boolean ignoreCase = expr.getArgument(0).asBooleanLiteralExpr().getValue();
        final Expression argument = expr.getArgument(1);
        final String type = fqcn(expr.getType().asString());
        final String method;
        if (type.endsWith("StringContains")) {
            method = ignoreCase ? "containsStringIgnoringCase" : "containsString";
        } else if (type.endsWith("StringStartsWith")) {
            method = ignoreCase ? "startsWithIgnoringCase" : "startsWith";
        } else {
            throw new IllegalStateException(String.format("Unsupported matcher type %s", type));
        }
        return String.format("%s.%s(%s)", type, method, argument);
    }

    private static String fqcn(final String type) {
        if (type.contains(".")) {
            return type;
        }
        return "org.hamcrest.core." + type;
    }

}
