package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class Main {

    private static final String STRING_CONTAINS = "org.hamcrest.core.StringContains";

    private static final String STRING_STARTS_WITH = "org.hamcrest.core.StringStartsWith";

    private static final String STRING_CONTAINS_SIMPLE = "StringContains";

    private static final String STRING_STARTS_WITH_SIMPLE = "StringStartsWith";

    private static final String ALL_OF = "org.hamcrest.core.AllOf";

    private static final String ALL_OF_SIMPLE = "AllOf";

    private static final String MATCHERS = "org.hamcrest.Matchers";

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaFile).forEach(Main::transform);
        }
    }

    private static boolean isJavaFile(final Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static void transform(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            if (replaceLegacyMatchers(unit)) {
                ensureMatchersImport(unit);
                unit.getImports().removeIf(entry -> !entry.isStatic()
                    && ALL_OF.equals(entry.getNameAsString()));
                Files.writeString(path, unit.toString(), StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static boolean replaceLegacyMatchers(final CompilationUnit unit) {
        boolean changed = false;
        for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
            final Optional<Expression> replacement = replacement(expr);
            if (replacement.isPresent()) {
                expr.replace(replacement.get());
                changed = true;
            }
        }
        return changed;
    }

    private static Optional<Expression> replacement(final ObjectCreationExpr expr) {
        if (expr.getArguments().isEmpty() || expr.getArguments().size() > 2) {
            return Optional.empty();
        }
        final String type = expr.getType().getNameAsString();
        if (matches(type, STRING_CONTAINS, STRING_CONTAINS_SIMPLE)) {
            return Optional.of(rewriteMatcher(expr));
        }
        if (matches(type, STRING_STARTS_WITH, STRING_STARTS_WITH_SIMPLE)) {
            return Optional.of(rewriteMatcher(expr));
        }
        if (matches(type, ALL_OF, ALL_OF_SIMPLE)) {
            return rewriteAllOf(expr);
        }
        return Optional.empty();
    }

    private static Expression rewriteMatcher(final ObjectCreationExpr expr) {
        final Expression text = expr.getArgument(expr.getArguments().size() - 1);
        final boolean useIgnoreCase = expr.getArguments().size() == 2
            && expr.getArgument(0).isBooleanLiteralExpr()
            && expr.getArgument(0).asBooleanLiteralExpr().getValue();
        if (matches(expr.getType().getNameAsString(), STRING_CONTAINS, STRING_CONTAINS_SIMPLE)) {
            return matcherCall(useIgnoreCase ? "containsStringIgnoringCase" : "containsString", text);
        }
        return matcherCall(useIgnoreCase ? "startsWithIgnoringCase" : "startsWith", text);
    }

    private static Optional<Expression> rewriteAllOf(final ObjectCreationExpr expr) {
        if (expr.getArguments().size() != 1) {
            return Optional.empty();
        }
        final Expression argument = expr.getArgument(0);
        if (!argument.isMethodCallExpr()) {
            return Optional.empty();
        }
        final MethodCallExpr call = argument.asMethodCallExpr();
        if (!"Arrays".equals(call.getScope().map(Expression::toString).orElse(""))
            || !"asList".equals(call.getNameAsString())) {
            return Optional.empty();
        }
        final MethodCallExpr matcher = new MethodCallExpr(new NameExpr("Matchers"), "allOf");
        call.getArguments().forEach(arg -> matcher.addArgument(arg.clone()));
        return Optional.of(matcher);
    }

    private static boolean matches(final String actual, final String fqcn, final String simple) {
        return fqcn.equals(actual) || simple.equals(actual);
    }

    private static MethodCallExpr matcherCall(final String method, final Expression arg) {
        final MethodCallExpr call = new MethodCallExpr(new NameExpr("Matchers"), method);
        call.addArgument(arg.clone());
        return call;
    }

    private static void ensureMatchersImport(final CompilationUnit unit) {
        final boolean imported = unit.getImports().stream()
            .anyMatch(entry -> !entry.isStatic() && MATCHERS.equals(entry.getNameAsString())
                || !entry.isStatic() && "org.hamcrest.Matchers".equals(entry.getNameAsString()));
        if (!imported) {
            unit.addImport(MATCHERS);
        }
    }

    private static boolean hasAllOfImport(final CompilationUnit unit) {
        return unit.getImports().stream()
            .anyMatch(entry -> !entry.isStatic() && (ALL_OF.equals(entry.getNameAsString())
                || ALL_OF_SIMPLE.equals(entry.getNameAsString())));
    }
}
