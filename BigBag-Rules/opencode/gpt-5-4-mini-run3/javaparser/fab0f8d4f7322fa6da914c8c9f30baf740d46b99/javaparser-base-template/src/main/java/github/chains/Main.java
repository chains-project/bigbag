package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class Main {

    private static final String TARGET_METHOD = "accept";

    private static final String TARGET_HEADERS_TYPE = "com.artipie.http.Headers.From";

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(Main::transform);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(unit);
            final AtomicBoolean changed = new AtomicBoolean(false);
            unit.findAll(MethodCallExpr.class).forEach(call -> {
                if (shouldWrapSecondArgument(call)) {
                    final Expression argument = call.getArgument(1);
                    call.setArgument(1, wrapHeaders(argument));
                    changed.set(true);
                }
            });
            if (changed.get()) {
                Files.write(file, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform " + file, err);
        }
    }

    private static boolean shouldWrapSecondArgument(final MethodCallExpr call) {
        return TARGET_METHOD.equals(call.getNameAsString())
            && call.getArguments().size() == 3
            && !isHeadersFactory(call.getArgument(1));
    }

    private static boolean isHeadersFactory(final Expression expr) {
        return expr.isObjectCreationExpr()
            && expr.asObjectCreationExpr().getType().asString().endsWith("Headers.From");
    }

    private static ObjectCreationExpr wrapHeaders(final Expression expr) {
        return new ObjectCreationExpr(
            null,
            new ClassOrInterfaceType(null, TARGET_HEADERS_TYPE),
            com.github.javaparser.ast.NodeList.nodeList(expr.clone())
        );
    }
}
