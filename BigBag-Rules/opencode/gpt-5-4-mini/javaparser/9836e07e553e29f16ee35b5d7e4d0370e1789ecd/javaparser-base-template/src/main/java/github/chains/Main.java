package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    public static void main(final String[] args) throws IOException {
        final Path source = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(source)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transform);
        }
    }

    private static void transform(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(unit);
            boolean changed = unit.findAll(ObjectCreationExpr.class).stream()
                .filter(Main::isTargetCreation)
                .map(Main::rewriteCreation)
                .reduce(false, Boolean::logicalOr);
            changed = changed || unit.findAll(MethodCallExpr.class).stream()
                .filter(Main::isTargetMethodCall)
                .map(Main::rewriteMethodCall)
                .reduce(false, Boolean::logicalOr);
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(String.format("Failed to process %s", path), ex);
        }
    }

    private static boolean isTargetCreation(final ObjectCreationExpr expr) {
        final String type = expr.getType().asString();
        return isHamcrestSubstringMatcher(type)
            && expr.getArguments().size() == 2;
    }

    private static boolean isHamcrestSubstringMatcher(final String type) {
        return "StringContains".equals(type)
            || "StringStartsWith".equals(type)
            || "StringEndsWith".equals(type);
    }

    private static boolean rewriteCreation(final ObjectCreationExpr expr) {
        final String first = expr.getArgument(0).toString();
        final String second = expr.getArgument(1).toString();
        if (!(expr.getArgument(0) instanceof BooleanLiteralExpr)) {
            return false;
        }
        final String method = methodName(expr.getType().asString(), ((BooleanLiteralExpr) expr.getArgument(0)).getValue());
        expr.replace(StaticJavaParser.parseExpression(String.format("org.hamcrest.Matchers.%s(%s)", method, second)));
        return true;
    }

    private static String methodName(final String type, final boolean ignoreCase) {
        if ("StringContains".equals(type)) {
            return "containsString";
        }
        if ("StringStartsWith".equals(type)) {
            return "startsWith";
        }
        if ("StringEndsWith".equals(type)) {
            return "endsWith";
        }
        throw new IllegalArgumentException(String.format("Unsupported type: %s", type));
    }

    private static boolean isTargetMethodCall(final MethodCallExpr expr) {
        if (!expr.getScope().isPresent() || expr.getArguments().size() != 1) {
            return false;
        }
        final Expression scope = expr.getScope().get();
        final String owner = scope.toString();
        return ("StringContains".equals(owner)
            || "StringStartsWith".equals(owner)
            || "StringEndsWith".equals(owner))
            && isHamcrestFactory(expr.getNameAsString());
    }

    private static boolean isHamcrestFactory(final String name) {
        return "containsString".equals(name)
            || "startsWith".equals(name)
            || "endsWith".equals(name)
            || "containsStringIgnoringCase".equals(name)
            || "startsWithIgnoringCase".equals(name)
            || "endsWithIgnoringCase".equals(name);
    }

    private static boolean rewriteMethodCall(final MethodCallExpr expr) {
        if ("containsStringIgnoringCase".equals(expr.getNameAsString())) {
            expr.setName("containsString");
        } else if ("startsWithIgnoringCase".equals(expr.getNameAsString())) {
            expr.setName("startsWith");
        } else if ("endsWithIgnoringCase".equals(expr.getNameAsString())) {
            expr.setName("endsWith");
        }
        expr.setScope(StaticJavaParser.parseExpression("org.hamcrest.Matchers"));
        return true;
    }

}
