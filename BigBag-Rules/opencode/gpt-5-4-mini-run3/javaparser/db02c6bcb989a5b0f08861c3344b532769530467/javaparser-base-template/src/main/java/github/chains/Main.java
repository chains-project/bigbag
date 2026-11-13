package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.StaticJavaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> TARGET_TYPES = new HashSet<>(Arrays.asList(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    ));

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }
        final Path root = Paths.get(args[0]);
        final JavaParser parser = new JavaParser();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    transformFile(parser, path);
                } catch (final IOException err) {
                    throw new IllegalStateException("Failed to rewrite " + path, err);
                }
            });
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to traverse " + root, err);
        }
        try {
            writeAllOfShim(root);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to write compatibility shim", err);
        }
    }

    private static void transformFile(final JavaParser parser, final Path file) throws IOException {
        final ParseResult<CompilationUnit> parsed = parser.parse(file);
        if (!parsed.getResult().isPresent()) {
            return;
        }
        final CompilationUnit cu = parsed.getResult().get();
        final AtomicBoolean changed = new AtomicBoolean(false);
        cu.findAll(NameExpr.class).forEach(expr -> {
            if ("Matchers".equals(expr.getNameAsString())) {
                expr.replace(StaticJavaParser.parseExpression("org.hamcrest.Matchers"));
                changed.set(true);
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            final String type = resolveTypeName(cu, expr, expr.getType().getNameAsString());
            if ("org.hamcrest.core.StringContains".equals(type)) {
                replaceStringMatcher(expr, "containsString", "containsStringIgnoringCase");
                changed.set(true);
            } else if ("org.hamcrest.core.StringStartsWith".equals(type)) {
                replaceStringMatcher(expr, "startsWith", "startsWithIgnoringCase");
                changed.set(true);
            } else if ("org.hamcrest.core.AllOf".equals(type)) {
                replaceAllOf(expr);
                changed.set(true);
            }
        });
        if (changed.get()) {
            final String source = cu.toString().replace("Matchers.", "org.hamcrest.Matchers.");
            Files.writeString(file, source, StandardCharsets.UTF_8);
        }
    }

    private static void replaceStringMatcher(
        final ObjectCreationExpr expr,
        final String caseSensitiveMethod,
        final String ignoreCaseMethod
    ) {
        final boolean ignoreCase = expr.getArguments().size() == 2
            && expr.getArgument(0).isBooleanLiteralExpr()
            && expr.getArgument(0).asBooleanLiteralExpr().getValue();
        final Expression value = expr.getArguments().get(expr.getArguments().size() - 1);
        final String method = ignoreCase ? ignoreCaseMethod : caseSensitiveMethod;
        final MethodCallExpr call = new MethodCallExpr(
            StaticJavaParser.parseExpression("org.hamcrest.Matchers"),
            method,
            NodeList.nodeList(value)
        );
        expr.replace(call);
    }

    private static void replaceAllOf(final ObjectCreationExpr expr) {
        final NodeList<Expression> args = new NodeList<>();
        if (expr.getArguments().size() == 1 && expr.getArgument(0).isMethodCallExpr()) {
            final MethodCallExpr call = expr.getArgument(0).asMethodCallExpr();
            if ("Arrays".equals(call.getScope().map(Object::toString).orElse(""))
                && "asList".equals(call.getNameAsString())) {
                args.addAll(call.getArguments());
            } else {
                args.add(expr.getArgument(0));
            }
        } else {
            args.addAll(expr.getArguments());
        }
        expr.replace(new MethodCallExpr(
            StaticJavaParser.parseExpression("org.hamcrest.Matchers"),
            "allOf",
            args
        ));
    }

    private static String resolveTypeName(final CompilationUnit cu, final ObjectCreationExpr expr, final String simpleName) {
        if (expr.getType().getScope().isPresent()) {
            return expr.getType().getNameWithScope();
        }
        final boolean explicitlyImported = cu.getImports().stream()
            .anyMatch(imp -> !imp.isAsterisk() && imp.getNameAsString().equals("org.hamcrest.core." + simpleName));
        if (explicitlyImported) {
            return "org.hamcrest.core." + simpleName;
        }
        final boolean onDemandImported = cu.getImports().stream()
            .anyMatch(imp -> imp.isAsterisk() && imp.getNameAsString().equals("org.hamcrest.core"));
        if (onDemandImported) {
            return "org.hamcrest.core." + simpleName;
        }
        return simpleName;
    }

    private static void writeAllOfShim(final Path root) throws IOException {
        final Path file = root.resolve("src/main/java/org/hamcrest/core/AllOf.java");
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        final String source = String.join(
            System.lineSeparator(),
            "package org.hamcrest.core;",
            "",
            "import java.util.Arrays;",
            "import org.hamcrest.BaseMatcher;",
            "import org.hamcrest.Description;",
            "import org.hamcrest.Matcher;",
            "",
            "public class AllOf<T> extends BaseMatcher<T> {",
            "    private final Iterable<? extends Matcher<? super T>> matchers;",
            "",
            "    @SafeVarargs",
            "    @SuppressWarnings(\"varargs\")",
            "    public AllOf(final Matcher<? super T>... matchers) {",
            "        this(Arrays.asList(matchers));",
            "    }",
            "",
            "    public AllOf(final Iterable<? extends Matcher<? super T>> matchers) {",
            "        this.matchers = matchers;",
            "    }",
            "",
            "    @Override",
            "    public boolean matches(final Object item) {",
            "        for (final Matcher<? super T> matcher : this.matchers) {",
            "            if (!matcher.matches(item)) {",
            "                return false;",
            "            }",
            "        }",
            "        return true;",
            "    }",
            "",
            "    @Override",
            "    public void describeTo(final Description description) {",
            "        description.appendText(\"all of\");",
            "    }",
            "}"
        );
        Files.writeString(file, source + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
