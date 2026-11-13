package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
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

    private static final Map<String, String[]> MATCHERS = new HashMap<>();

    static {
        MATCHERS.put(
            "StringContains",
            new String[] {"StringContains", "containsString", "containsStringIgnoringCase"}
        );
        MATCHERS.put(
            "StringStartsWith",
            new String[] {"StringStartsWith", "startsWith", "startsWithIgnoringCase"}
        );
        MATCHERS.put(
            "StringEndsWith",
            new String[] {"StringEndsWith", "endsWith", "endsWithIgnoringCase"}
        );
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }
        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(String.format("Not a directory: %s", root));
        }
        try (Stream<Path> paths = Files.walk(root)) {
            final boolean[] allOf = {false};
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                allOf[0] |= rewrite(path);
            });
            if (allOf[0]) {
                ensureAllOfShim(root);
            }
        }
    }

    private static boolean rewrite(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            boolean changed = false;
            for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
                changed |= rewriteMatcher(expr);
            }
            for (final MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                changed |= rewriteMatcher(call);
            }
            if (changed) {
                Files.writeString(path, unit.toString(), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to process %s", path), err);
        }
    }

    private static boolean rewriteMatcher(final ObjectCreationExpr expr) {
        if ("AllOf".equals(expr.getType().getNameAsString())) {
            final String replacement = String.format(
                "org.hamcrest.Matchers.allOf(%s)",
                expr.getArguments().get(0)
            );
            expr.replace(StaticJavaParser.parseExpression(replacement));
            return true;
        }
        final String[] matcher = MATCHERS.get(expr.getType().getNameAsString());
        if (matcher == null) {
            return false;
        }
        if (expr.getArguments().size() != 2) {
            return false;
        }
        final Expression flag = expr.getArgument(0);
        if (!(flag instanceof BooleanLiteralExpr)) {
            return false;
        }
        final String method = ((BooleanLiteralExpr) flag).getValue() ? matcher[2] : matcher[1];
        final String replacement = String.format(
            "org.hamcrest.Matchers.%s(%s)",
            method,
            expr.getArgument(1)
        );
        expr.replace(StaticJavaParser.parseExpression(replacement));
        return true;
    }

    private static boolean rewriteMatcher(final MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return false;
        }
        final Expression scope = call.getScope().get();
        final String scopeName = scope.toString();
        final String[] matcher = MATCHERS.values().stream()
            .filter(candidate -> scopeName.endsWith(candidate[0]))
            .findFirst()
            .orElse(null);
        if (matcher == null || call.getArguments().size() != 1) {
            return false;
        }
        if (!call.getNameAsString().equals(matcher[1]) && !call.getNameAsString().equals(matcher[2])) {
            return false;
        }
        final String replacement = String.format(
            "org.hamcrest.Matchers.%s(%s)",
            methodFor(call.getNameAsString()),
            call.getArgument(0)
        );
        call.replace(StaticJavaParser.parseExpression(replacement));
        return true;
    }

    private static void ensureAllOfShim(final Path root) {
        final Path file = root.resolve(Paths.get("org", "hamcrest", "core", "AllOf.java"));
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                file,
                String.join(
                    System.lineSeparator(),
                    "package org.hamcrest.core;",
                    "",
                    "import java.util.Arrays;",
                    "import org.hamcrest.BaseMatcher;",
                    "import org.hamcrest.Description;",
                    "import org.hamcrest.Matcher;",
                    "",
                    "public final class AllOf<T> extends BaseMatcher<T> {",
                    "    private final Matcher<T> delegate;",
                    "",
                    "    @SafeVarargs",
                    "    public AllOf(final Matcher<? super T>... matchers) {",
                    "        this.delegate = org.hamcrest.Matchers.allOf(matchers);",
                    "    }",
                    "",
                    "    public AllOf(final Iterable<Matcher<? super T>> matchers) {",
                    "        this.delegate = org.hamcrest.Matchers.allOf(matchers);",
                    "    }",
                    "",
                    "    @Override",
                    "    public boolean matches(final Object item) {",
                    "        return this.delegate.matches(item);",
                    "    }",
                    "",
                    "    @Override",
                    "    public void describeTo(final Description description) {",
                    "        this.delegate.describeTo(description);",
                    "    }",
                    "}"
                ),
                StandardCharsets.UTF_8
            );
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to create shim %s", file), err);
        }
    }

    private static String methodFor(final String name) {
        if ("containsStringIgnoringCase".equals(name)) {
            return name;
        }
        if ("startsWithIgnoringCase".equals(name)) {
            return name;
        }
        if ("endsWithIgnoringCase".equals(name)) {
            return name;
        }
        return name;
    }
}
