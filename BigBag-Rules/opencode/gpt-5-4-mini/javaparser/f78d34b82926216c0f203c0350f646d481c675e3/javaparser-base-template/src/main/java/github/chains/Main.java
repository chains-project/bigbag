package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "develop.p2p.lib";
    private static final String NEW_PREFIX = "tokyo.peya.lib";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan source tree: " + root, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(Files.readString(file, StandardCharsets.UTF_8));
            DependencyPackageRenamer renamer = new DependencyPackageRenamer();
            renamer.visit(compilationUnit, null);
            if (renamer.changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static final class DependencyPackageRenamer extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            if (n.getNameAsString().startsWith(OLD_PREFIX)) {
                n.setName(replacePrefix(n.getNameAsString()));
                changed = true;
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            if (n.toString().startsWith(OLD_PREFIX)) {
                changed = true;
                return StaticJavaParser.parseType(replacePrefix(n.toString()));
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(NameExpr n, Void arg) {
            if (n.getNameAsString().startsWith(OLD_PREFIX)) {
                changed = true;
                return new NameExpr(new SimpleName(replacePrefix(n.getNameAsString())));
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(FieldAccessExpr n, Void arg) {
            if (n.toString().startsWith(OLD_PREFIX)) {
                changed = true;
                return StaticJavaParser.parseExpression(replacePrefix(n.toString()));
            }
            return (Node) super.visit(n, arg);
        }
    }

    private static String replacePrefix(String text) {
        return text.startsWith(OLD_PREFIX) ? NEW_PREFIX + text.substring(OLD_PREFIX.length()) : text;
    }
}
