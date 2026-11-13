package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String STRING_CONTAINS = "org.hamcrest.core.StringContains";

    private static final String STRING_STARTS_WITH = "org.hamcrest.core.StringStartsWith";

    private static final String HAMCREST_MATCHERS = "org.hamcrest.CoreMatchers";

    public static void main(final String[] args) {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]);
        try {
            Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to process " + root, err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(unit);
            final List<ObjectCreationExpr> creations = new ArrayList<>(unit.findAll(ObjectCreationExpr.class));
            final List<MethodCallExpr> calls = new ArrayList<>(unit.findAll(MethodCallExpr.class));
            boolean changed = false;
            for (final ObjectCreationExpr expr : creations) {
                changed |= rewrite(expr);
            }
            for (final MethodCallExpr call : calls) {
                changed |= rewrite(call);
            }
            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform " + file, err);
        }
    }

    private static boolean rewrite(final ObjectCreationExpr expr) {
        final String type = expr.getType().asString();
        if (expr.getArguments().size() != 2) {
            return false;
        }
        if (type.equals(STRING_CONTAINS) || type.equals("StringContains")) {
            expr.replace(rewriteMatcher(expr.getArgument(0), expr.getArgument(1), true));
            return true;
        }
        if (type.equals(STRING_STARTS_WITH) || type.equals("StringStartsWith")) {
            expr.replace(rewriteMatcher(expr.getArgument(0), expr.getArgument(1), false));
            return true;
        }
        return false;
    }

    private static boolean rewrite(final MethodCallExpr call) {
        final String scope = call.getScope().map(Object::toString).orElse("");
        if (call.getArguments().size() != 1) {
            return false;
        }
        if (scope.equals(STRING_CONTAINS) && call.getNameAsString().equals("containsStringIgnoringCase")) {
            call.replace(ignoreCaseMatcher(call.getArgument(0), true));
            return true;
        }
        if (scope.equals(STRING_STARTS_WITH) && call.getNameAsString().equals("startsWithIgnoringCase")) {
            call.replace(ignoreCaseMatcher(call.getArgument(0), false));
            return true;
        }
        return false;
    }

    private static Expression rewriteMatcher(
        final Expression mode,
        final Expression text,
        final boolean contains
    ) {
        if (mode.isBooleanLiteralExpr() && mode.asBooleanLiteralExpr().getValue()) {
            return ignoreCaseMatcher(text, contains);
        }
        return caseSensitiveMatcher(text, contains);
    }

    private static Expression caseSensitiveMatcher(final Expression text, final boolean contains) {
        final String factory = contains ? "containsString" : "startsWith";
        return StaticJavaParser.parseExpression(String.format(
            "%s.%s(%s)",
            HAMCREST_MATCHERS,
            factory,
            text
        ));
    }

    private static Expression ignoreCaseMatcher(final Expression text, final boolean contains) {
        final String condition = contains
            ? String.format("item.toLowerCase().contains(%s.toString().toLowerCase())", text)
            : String.format("item.toLowerCase().startsWith(%s.toString().toLowerCase())", text);
        return StaticJavaParser.parseExpression(String.format(
            "new org.hamcrest.TypeSafeMatcher<String>() {"
                + "protected boolean matchesSafely(String item) { return %s; }"
                + "public void describeTo(org.hamcrest.Description description) { description.appendText(\"a string %s ignoring case\").appendValue(%s); }"
                + "}",
            condition,
            contains ? "containing" : "starting with",
            text
        ));
    }
}
