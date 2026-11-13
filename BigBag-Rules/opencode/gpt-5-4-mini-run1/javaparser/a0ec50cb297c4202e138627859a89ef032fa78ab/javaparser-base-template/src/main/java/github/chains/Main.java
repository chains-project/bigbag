package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source root path argument");
        }
        final Path source = Paths.get(args[0]);
        try {
            Files.walk(source)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to traverse source tree", err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            final AtomicBoolean changed = new AtomicBoolean(false);
            unit.accept(new HexTransform(changed), null);
            unit.getImports().removeIf(
                imp -> "org.cactoos.io.BytesOf".equals(imp.getNameAsString())
                    || "org.cactoos.text.HexOf".equals(imp.getNameAsString())
                    || "org.cactoos.list.ListOf".equals(imp.getNameAsString())
            );
            if (changed.get()) {
                unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(cls -> !cls.isInterface())
                    .findFirst()
                    .ifPresent(Main::ensureHelper);
                Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
            }
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }

    private static void ensureHelper(final ClassOrInterfaceDeclaration cls) {
        if (!cls.getMethodsByName("bytesToHex").isEmpty()) {
            return;
        }
        final MethodDeclaration helper = StaticJavaParser.parseBodyDeclaration(
            "private static String bytesToHex(final byte[] bytes) {"
                + "final char[] chars = new char[bytes.length * 2];"
                + "final char[] digits = \"0123456789abcdef\".toCharArray();"
                + "for (int idx = 0; idx < bytes.length; idx++) {"
                + "final int value = bytes[idx] & 0xFF;"
                + "chars[idx * 2] = digits[value >>> 4];"
                + "chars[idx * 2 + 1] = digits[value & 0x0F];"
                + "}"
                + "return new String(chars);"
                + "}"
        ).asMethodDeclaration();
        cls.addMember(helper);
    }

    private static final class HexTransform extends ModifierVisitor<Void> {

        private final AtomicBoolean changed;

        private HexTransform(final AtomicBoolean changed) {
            this.changed = changed;
        }

        @Override
        public Visitable visit(final MethodCallExpr call, final Void arg) {
            final Visitable visited = super.visit(call, arg);
            if (!(visited instanceof MethodCallExpr)) {
                return visited;
            }
            final MethodCallExpr current = (MethodCallExpr) visited;
            if (!"asString".equals(current.getNameAsString()) || current.getScope().isEmpty()) {
                return current;
            }
            final Expression scope = current.getScope().get();
            if (!(scope instanceof ObjectCreationExpr)) {
                return current;
            }
            final ObjectCreationExpr outer = (ObjectCreationExpr) scope;
            if (!"HexOf".equals(outer.getType().getNameAsString()) || outer.getArguments().size() != 1) {
                return current;
            }
            final Expression inner = outer.getArgument(0);
            if (!(inner instanceof ObjectCreationExpr)) {
                return current;
            }
            final ObjectCreationExpr bytes = (ObjectCreationExpr) inner;
            if (!"BytesOf".equals(bytes.getType().getNameAsString()) || bytes.getArguments().size() != 1) {
                return current;
            }
            this.changed.set(true);
            return new MethodCallExpr("bytesToHex", bytes.getArgument(0).clone());
        }

        @Override
        public Visitable visit(final ObjectCreationExpr creation, final Void arg) {
            final Visitable visited = super.visit(creation, arg);
            if (!(visited instanceof ObjectCreationExpr)) {
                return visited;
            }
            final ObjectCreationExpr current = (ObjectCreationExpr) visited;
            if (!"ListOf".equals(current.getType().getNameAsString())) {
                return current;
            }
            this.changed.set(true);
            return new MethodCallExpr(
                StaticJavaParser.parseExpression("java.util.List"),
                StaticJavaParser.parseSimpleName("of"),
                new NodeList<>(current.getArguments())
            );
        }
    }
}
