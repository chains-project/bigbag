package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String CACTOOS_BYTES = "org.cactoos.io.BytesOf";

    private static final String CACTOOS_HEX = "org.cactoos.text.HexOf";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> sources = paths
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            for (final Path source : sources) {
                transform(source);
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform sources", err);
        }
    }

    private static void transform(final Path source) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);
        final HexOfBytesRewriter rewriter = new HexOfBytesRewriter();
        rewriter.visit(cu, null);
        if (!rewriter.changed()) {
            return;
        }
        cu.getImports().removeIf(
            imp -> imp.getNameAsString().equals(CACTOOS_BYTES)
                || imp.getNameAsString().equals(CACTOOS_HEX)
        );
        Files.writeString(source, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static final class HexOfBytesRewriter extends ModifierVisitor<Void> {

        private boolean changed;

        boolean changed() {
            return this.changed;
        }

        @Override
        public Visitable visit(final MethodCallExpr expr, final Void arg) {
            final Optional<Expression> scope = expr.getScope();
            if ("asString".equals(expr.getNameAsString()) && scope.isPresent()) {
                final Expression outer = scope.get();
                if (outer instanceof ObjectCreationExpr) {
                    final ObjectCreationExpr hex = (ObjectCreationExpr) outer;
                    if (matchesSimpleName(hex, CACTOOS_HEX) && hex.getArguments().size() == 1) {
                        final Expression inner = hex.getArgument(0);
                        if (inner instanceof ObjectCreationExpr) {
                            final ObjectCreationExpr bytes = (ObjectCreationExpr) inner;
                            if (matchesSimpleName(bytes, CACTOOS_BYTES)
                                && bytes.getArguments().size() == 1) {
                                final Expression data = bytes.getArgument(0);
                                this.changed = true;
                                ensureHelper(expr);
                                return new MethodCallExpr("bytesToHex", data.clone());
                            }
                        }
                    }
                }
            }
            return super.visit(expr, arg);
        }

        private void ensureHelper(final MethodCallExpr expr) {
            expr.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> {
                final boolean exists = clazz.getMethodsBySignature("bytesToHex", "byte[]")
                    .stream().findAny().isPresent();
                if (!exists) {
                    final BodyDeclaration<?> helper = StaticJavaParser.parseBodyDeclaration(
                        "private static String bytesToHex(final byte[] data) {"
                            + "final StringBuilder out = new StringBuilder(data.length * 2);"
                            + "for (final byte datum : data) {"
                            + "out.append(Character.forDigit((datum >> 4) & 0xF, 16));"
                            + "out.append(Character.forDigit(datum & 0xF, 16));"
                            + "}"
                            + "return out.toString();"
                            + "}"
                    );
                    clazz.addMember(helper);
                }
            });
        }

        private boolean matchesSimpleName(final ObjectCreationExpr expr, final String name) {
            return expr.getType().getNameAsString().equals(name.substring(name.lastIndexOf('.') + 1));
        }
    }
}
