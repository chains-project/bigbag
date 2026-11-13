package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try {
            final List<Path> files = Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
            for (final Path file : files) {
                transform(file);
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform sources at " + root, err);
        }
    }

    private static void transform(final Path file) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        boolean changed = false;
        final List<ObjectCreationExpr> bytesCalls = unit.findAll(ObjectCreationExpr.class).stream()
            .filter(Main::isBytesOf)
            .collect(Collectors.toList());
        for (final ObjectCreationExpr bytes : bytesCalls) {
            bytes.replace(bytes.getArgument(0).clone());
            changed = true;
        }
        final List<ObjectCreationExpr> listCalls = unit.findAll(ObjectCreationExpr.class).stream()
            .filter(Main::isListOf)
            .collect(Collectors.toList());
        for (final ObjectCreationExpr list : listCalls) {
            final String args = list.getArguments().stream()
                .map(Expression::toString)
                .collect(Collectors.joining(", "));
            list.replace(StaticJavaParser.parseExpression("java.util.Arrays.asList(" + args + ")"));
            changed = true;
        }
        final List<MethodCallExpr> hexCalls = unit.findAll(MethodCallExpr.class).stream()
            .filter(Main::isOldDigestCall)
            .collect(Collectors.toList());
        for (final MethodCallExpr call : hexCalls) {
            final ObjectCreationExpr hex = (ObjectCreationExpr) call.getScope().get();
            final Expression arg = hex.getArgument(0).clone();
            call.replace(new MethodCallExpr(null, "hex", new NodeList<>(arg)));
            changed = true;
            ensureHexHelper(call.findAncestor(ClassOrInterfaceDeclaration.class));
        }
        if (changed) {
            unit.getImports().removeIf(imp -> {
                final String name = imp.getNameAsString();
                return "org.cactoos.io.BytesOf".equals(name)
                    || "org.cactoos.text.HexOf".equals(name)
                    || "org.cactoos.list.ListOf".equals(name);
            });
            Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isOldDigestCall(final MethodCallExpr call) {
        if (!"asString".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
            return false;
        }
        final Expression scope = call.getScope().get();
        if (!(scope instanceof ObjectCreationExpr)) {
            return false;
        }
        final ObjectCreationExpr hex = (ObjectCreationExpr) scope;
        if (!"HexOf".equals(hex.getType().getNameAsString()) || hex.getArguments().size() != 1) {
            return false;
        }
        final Expression inner = hex.getArgument(0);
        if (!(inner instanceof ObjectCreationExpr)) {
            return false;
        }
        final ObjectCreationExpr bytes = (ObjectCreationExpr) inner;
        return "BytesOf".equals(bytes.getType().getNameAsString()) && bytes.getArguments().size() == 1;
    }

    private static boolean isBytesOf(final ObjectCreationExpr expr) {
        return "BytesOf".equals(expr.getType().getNameAsString()) && expr.getArguments().size() == 1;
    }

    private static boolean isListOf(final ObjectCreationExpr expr) {
        return "ListOf".equals(expr.getType().getNameAsString()) && !expr.getArguments().isEmpty();
    }

    private static void ensureHexHelper(final Optional<ClassOrInterfaceDeclaration> owner) {
        if (owner.isEmpty() || owner.get().getMethodsBySignature("hex", "byte[]").size() > 0) {
            return;
        }
        final MethodDeclaration method = owner.get().addMethod("hex");
        method.setPrivate(true);
        method.setStatic(true);
        method.setType("String");
        method.addParameter("byte[]", "bytes");
        final BlockStmt body = StaticJavaParser.parseBlock("{" +
            "final char[] hex = new char[bytes.length * 2];" +
            "for (int idx = 0; idx < bytes.length; idx++) {" +
            "  final int val = bytes[idx] & 0xFF;" +
            "  hex[idx * 2] = Character.forDigit(val >>> 4, 16);" +
            "  hex[idx * 2 + 1] = Character.forDigit(val & 0x0F, 16);" +
            "}" +
            "return new String(hex);" +
            "}");
        method.setBody(body);
    }
}
