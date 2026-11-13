package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Main {

    private static final String HEX_OF = "org.cactoos.text.HexOf";

    private static final String BYTES_OF = "org.cactoos.io.BytesOf";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> sources = paths
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
            for (final Path source : sources) {
                transform(source);
            }
        }
    }

    private static void transform(final Path source) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(unit);
        boolean changed = false;
        for (final ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            if (isType(creation, "org.cactoos.list.ListOf")) {
                creation.replace(buildJavaListOf(creation.getArguments()));
                changed = true;
                continue;
            }
            if (creation.getArguments().size() == 1) {
                final Optional<Expression> replacement = unwrapHexDigest(creation.getArgument(0));
                if (replacement.isPresent()) {
                    creation.setArgument(0, replacement.get());
                    changed = true;
                }
            }
        }
        if (removeUnusedImport(unit, HEX_OF)) {
            changed = true;
        }
        if (removeUnusedImport(unit, BYTES_OF)) {
            changed = true;
        }
        if (removeUnusedImport(unit, "org.cactoos.list.ListOf")) {
            changed = true;
        }
        if (changed) {
            Files.write(source, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Expression buildJavaListOf(final NodeList<Expression> args) {
        final MethodCallExpr call = StaticJavaParser.parseExpression("java.util.List.of()").asMethodCallExpr();
        call.setArguments(args.stream().map(Expression::clone).collect(NodeList.toNodeList()));
        return call;
    }

    private static Optional<Expression> unwrapHexDigest(final Expression expression) {
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }
        final MethodCallExpr call = expression.asMethodCallExpr();
        if (!"asString".equals(call.getNameAsString()) || call.getArguments().size() != 0) {
            return Optional.empty();
        }
        if (call.getScope().isEmpty() || !call.getScope().get().isObjectCreationExpr()) {
            return Optional.empty();
        }
        final ObjectCreationExpr hex = call.getScope().get().asObjectCreationExpr();
        if (!isType(hex, HEX_OF) || hex.getArguments().size() != 1) {
            return Optional.empty();
        }
        final Expression inner = hex.getArgument(0);
        if (!inner.isObjectCreationExpr()) {
            return Optional.empty();
        }
        final ObjectCreationExpr bytes = inner.asObjectCreationExpr();
        if (!isType(bytes, BYTES_OF) || bytes.getArguments().size() != 1) {
            return Optional.empty();
        }
        final MethodCallExpr encoded = StaticJavaParser
            .parseExpression("org.apache.commons.codec.binary.Hex.encodeHexString(null)")
            .asMethodCallExpr();
        encoded.setArgument(0, bytes.getArgument(0).clone());
        return Optional.of(encoded);
    }

    private static boolean isType(final ObjectCreationExpr expr, final String fqcn) {
        final String type = expr.getType().getNameAsString();
        final String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return type.equals(fqcn) || type.equals(simple) || type.endsWith('.' + simple);
    }

    private static boolean removeUnusedImport(final CompilationUnit unit, final String fqcn) {
        final NodeList<ImportDeclaration> imports = unit.getImports();
        for (final ImportDeclaration decl : imports) {
            if (!decl.isAsterisk() && decl.getNameAsString().equals(fqcn)) {
                decl.remove();
                return true;
            }
        }
        return false;
    }
}
