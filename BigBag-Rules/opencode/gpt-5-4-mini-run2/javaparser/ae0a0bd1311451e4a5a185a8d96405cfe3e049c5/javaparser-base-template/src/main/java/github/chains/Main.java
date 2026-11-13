package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final List<String> TARGET_TYPES = List.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith",
        "StringContains",
        "StringStartsWith"
    );

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to traverse %s", root), err);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final String source = Files.readString(file);
            final CompilationUnit cu = StaticJavaParser.parse(source);
            final List<Replacement> replacements = new ArrayList<>();
            final List<Integer> lineStarts = lineStarts(source);
            for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (shouldRewrite(expr)) {
                    final int start = offset(expr.getBegin().orElseThrow(), lineStarts);
                    final int end = offset(expr.getEnd().orElseThrow(), lineStarts) + 1;
                    final String replacement = String.format(
                        "%s(%s)",
                        expr.getType(),
                        expr.getArgument(1)
                    );
                    replacements.add(new Replacement(start, end, replacement));
                }
            }
            if (!replacements.isEmpty()) {
                String updated = source;
                for (final Replacement replacement : replacements.stream()
                    .sorted(Comparator.comparingInt((Replacement r) -> r.start).reversed())
                    .collect(Collectors.toList())) {
                    updated = updated.substring(0, replacement.start)
                        + replacement.text
                        + updated.substring(replacement.end);
                }
                Files.writeString(file, updated);
            }
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to rewrite %s", file), err);
        }
    }

    private static boolean shouldRewrite(final ObjectCreationExpr expr) {
        if (expr.getArguments().size() != 2) {
            return false;
        }
        final String name = expr.getType().getNameAsString();
        final String pkg = expr.getType().getScope().map(Object::toString).orElse("");
        final String fqcn = pkg.isEmpty() ? name : String.format("%s.%s", pkg, name);
        return TARGET_TYPES.contains(fqcn) || TARGET_TYPES.contains(name)
            || TARGET_TYPES.stream().anyMatch(target -> target.endsWith(name));
    }

    private static final class Replacement {

        private final int start;

        private final int end;

        private final String text;

        Replacement(final int start, final int end, final String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private static List<Integer> lineStarts(final String source) {
        final List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        starts.add(source.length());
        return starts;
    }

    private static int offset(final com.github.javaparser.Position pos, final List<Integer> starts) {
        return starts.get(pos.line - 1) + pos.column - 1;
    }
}
