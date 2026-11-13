package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Main {
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Input path does not exist: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteJavaFileSafely);
        }
    }

    private static void rewriteJavaFileSafely(Path path) {
        try {
            rewriteJavaFile(path);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static void rewriteJavaFile(Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        if (!original.contains(OLD_PACKAGE)) {
            return;
        }

        CompilationUnit cu = StaticJavaParser.parse(original);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PACKAGE)) {
                importDeclaration.setName(name.replaceFirst(java.util.regex.Pattern.quote(OLD_PACKAGE), NEW_PACKAGE));
                changed = true;
            }
        }

        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            String text = fieldAccessExpr.toString();
            if (text.contains(OLD_PACKAGE)) {
                Optional<Node> replacement = parseReplacementExpression(text.replace(OLD_PACKAGE, NEW_PACKAGE));
                if (replacement.isPresent()) {
                    fieldAccessExpr.replace(replacement.get());
                    changed = true;
                }
            }
        }

        if (!changed) {
            return;
        }

        Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static Optional<Node> parseReplacementExpression(String expression) {
        try {
            return Optional.of(StaticJavaParser.parseExpression(expression));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
