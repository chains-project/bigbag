package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser-based source transformation.
 */
public final class Main {

    /** Hamcrest substring matcher types affected by the constructor signature change. */
    private static final Set<String> TARGET_TYPES = new HashSet<>(List.of(
        "StringContains",
        "StringStartsWith"
    ));

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            for (final Path path : paths.filter(Main::isJavaSource).collect(Collectors.toList())) {
                transform(path);
            }
        }
    }

    private static boolean isJavaSource(final Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static void transform(final Path path) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(path);
        LexicalPreservingPrinter.setup(unit);
        boolean modified = false;
        for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
            if (shouldCollapseBooleanArgument(expr)) {
                expr.replace(StaticJavaParser.parseExpression(replacement(expr)));
                modified = true;
            }
        }
        if (modified) {
            Files.writeString(path, LexicalPreservingPrinter.print(unit), StandardCharsets.UTF_8);
        }
    }

    private static boolean shouldCollapseBooleanArgument(final ObjectCreationExpr expr) {
        return TARGET_TYPES.contains(expr.getType().getNameAsString())
            && expr.getArguments().size() == 2;
    }

    private static String replacement(final ObjectCreationExpr expr) {
        final boolean ignoreCase = expr.getArgument(0).isBooleanLiteralExpr()
            && expr.getArgument(0).asBooleanLiteralExpr().getValue();
        final String method;
        if ("StringContains".equals(expr.getType().getNameAsString())) {
            method = ignoreCase ? "containsStringIgnoringCase" : "containsString";
        } else {
            method = ignoreCase ? "startsWithIgnoringCase" : "startsWith";
        }
        return String.format(
            "org.hamcrest.core.%s.%s(%s)",
            expr.getType().getNameAsString(),
            method,
            expr.getArgument(1).toString()
        );
    }
}
