package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
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

    private static final String HAMCREST_CONTAINS = "StringContains";

    private static final String HAMCREST_STARTS_WITH = "StringStartsWith";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transform);
        } catch (IOException err) {
            throw new IllegalStateException(String.format("Failed to walk %s", root), err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(unit);
            final boolean[] changed = {false};
            unit.findAll(ObjectCreationExpr.class).forEach(expr -> {
                final String type = expr.getType().getNameAsString();
                if ((HAMCREST_CONTAINS.equals(type) || HAMCREST_STARTS_WITH.equals(type))
                    && expr.getArguments().size() == 2) {
                    expr.setArguments(NodeList.nodeList(expr.getArgument(1)));
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(unit), StandardCharsets.UTF_8);
            }
        } catch (IOException err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }
}
