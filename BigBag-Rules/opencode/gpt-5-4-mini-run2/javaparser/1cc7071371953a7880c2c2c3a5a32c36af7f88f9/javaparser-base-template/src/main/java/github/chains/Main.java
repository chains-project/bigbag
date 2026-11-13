package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single argument: source directory");
        }

        Path sourceRoot = Path.of(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                 .sorted(Comparator.naturalOrder())
                 .forEach(Main::rewrite);
        }
    }

    private static void rewrite(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                String importedName = importDeclaration.getNameAsString();
                if (importedName.startsWith(OLD_PREFIX)) {
                    importDeclaration.setName(importedName.replace(OLD_PREFIX, NEW_PREFIX));
                    changed = true;
                }
            }

            changed |= compilationUnit.findAll(Node.class).stream().map(Main::rewriteNode).anyMatch(Boolean.TRUE::equals);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite " + path, e);
        }
    }

    private static Boolean rewriteNode(Node node) {
        if (node instanceof ImportDeclaration) {
            return false;
        }

        if (node instanceof NodeWithName<?>) {
            NodeWithName<?> named = (NodeWithName<?>) node;
            String name = named.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                named.setName(name.replace(OLD_PREFIX, NEW_PREFIX));
                return true;
            }
        }

        if (node instanceof Type) {
            Type type = (Type) node;
            String text = type.toString();
            if (text.contains(OLD_PREFIX)) {
                Type replacement = StaticJavaParser.parseType(text.replace(OLD_PREFIX, NEW_PREFIX));
                type.replace(replacement);
                return true;
            }
        }

        if (node instanceof Expression) {
            Expression expression = (Expression) node;
            String text = expression.toString();
            if (text.contains(OLD_PREFIX)) {
                Expression replacement = StaticJavaParser.parseExpression(text.replace(OLD_PREFIX, NEW_PREFIX));
                expression.replace(replacement);
                return true;
            }
        }

        if (node instanceof SimpleName) {
            SimpleName simpleName = (SimpleName) node;
            String identifier = simpleName.asString();
            if (identifier.startsWith(OLD_PREFIX)) {
                simpleName.setIdentifier(identifier.replace(OLD_PREFIX, NEW_PREFIX));
                return true;
            }
        }

        if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            String text = nameExpr.toString();
            if (text.startsWith(OLD_PREFIX)) {
                nameExpr.replace(StaticJavaParser.parseExpression(text.replace(OLD_PREFIX, NEW_PREFIX)));
                return true;
            }
        }

        return false;
    }
}
