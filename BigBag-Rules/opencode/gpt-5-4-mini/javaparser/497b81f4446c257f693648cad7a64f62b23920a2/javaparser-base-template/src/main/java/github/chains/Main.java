package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_BYTES_OF = "BytesOf";

    private static final String OLD_HEX_OF = "HexOf";

    private static final String OLD_LIST_OF = "ListOf";

    private static final Pattern DIGEST_PATTERN = Pattern.compile(
        "new\\s+HexOf\\(new\\s+BytesOf\\((.*)\\)\\)\\.asString\\(\\)"
    );

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        final AtomicInteger changed = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> sources = paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
            for (Path source : sources) {
                if (transform(source)) {
                    changed.incrementAndGet();
                }
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk source tree: " + root, err);
        }
        System.out.printf("Updated %d files%n", changed.get());
    }

    private static boolean transform(final Path source) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(source);
        final boolean[] modified = {false};
        unit.findAll(ObjectCreationExpr.class).forEach(expr -> {
            final Expression replacement = collapseDigestHexEncoding(expr);
            final Expression listReplacement = replacement == null ? collapseListOf(expr) : null;
            if (replacement != null) {
                expr.setArguments(NodeList.nodeList(replacement));
                modified[0] = true;
            } else if (listReplacement != null) {
                expr.replace(listReplacement);
                modified[0] = true;
            }
        });
        if (removeUnusedImports(unit)) {
            modified[0] = true;
        }
        if (modified[0]) {
            Files.write(source, unit.toString().getBytes(StandardCharsets.UTF_8));
        }
        return modified[0];
    }

    private static boolean removeUnusedImports(final CompilationUnit unit) {
        final boolean removed = unit.getImports().removeIf(Main::isRemovedImport);
        final boolean removedList = unit.getImports().removeIf(Main::isRemovedListImport);
        return removed || removedList;
    }

    private static boolean isRemovedImport(final ImportDeclaration imprt) {
        final String name = imprt.getNameAsString();
        return name.equals("org.cactoos.io." + OLD_BYTES_OF)
            || name.equals("org.cactoos.text." + OLD_HEX_OF);
    }

    private static boolean isRemovedListImport(final ImportDeclaration imprt) {
        return imprt.getNameAsString().equals("org.cactoos.list." + OLD_LIST_OF);
    }

    private static Expression collapseDigestHexEncoding(final ObjectCreationExpr expr) {
        if (expr.getArguments().size() != 1) {
            return null;
        }
        final Expression argument = expr.getArgument(0);
        final Matcher matcher = DIGEST_PATTERN.matcher(argument.toString());
        if (!matcher.matches()) {
            return null;
        }
        return StaticJavaParser.parseExpression(
            "org.apache.commons.codec.binary.Hex.encodeHexString(" + matcher.group(1) + ")"
        );
    }

    private static Expression collapseListOf(final ObjectCreationExpr expr) {
        if (!expr.getType().getNameAsString().equals(OLD_LIST_OF)) {
            return null;
        }
        final String args = expr.getArguments().stream()
            .map(Expression::toString)
            .collect(Collectors.joining(", "));
        return StaticJavaParser.parseExpression("java.util.Arrays.asList(" + args + ")");
    }

    private static boolean isTypeNamed(final ObjectCreationExpr expr, final String simpleName) {
        final String name = expr.getType().asString();
        return name.equals(simpleName) || name.endsWith('.' + simpleName);
    }
}
