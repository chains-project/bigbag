package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        final String oldType = "org.cactoos.map.MapEntry";
        final String newType = "java.util.AbstractMap.SimpleEntry";
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> rewrite(parser, path, oldType, newType));
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to walk %s", sourceRoot), err);
        }
    }

    private static void rewrite(
        final JavaParser parser,
        final Path path,
        final String oldType,
        final String newType
    ) {
        try {
            final CompilationUnit unit = parser.parse(path).getResult()
                .orElseThrow(() -> new IllegalStateException(String.format("Can't parse %s", path)));
            final String oldSimple = simpleName(oldType);
            final String newSimple = simpleName(newType);
            boolean changed = false;
            changed |= unit.getImports().removeIf(imp -> Objects.equals(imp.getNameAsString(), oldType));
            for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
                final String created = expr.getType().getNameAsString();
                if (created.equals(oldSimple) || created.equals(oldType) || created.equals(simpleName(newType))) {
                    expr.setType(StaticJavaParser.parseClassOrInterfaceType(newType));
                    ((ClassOrInterfaceType) expr.getType()).setTypeArguments(new NodeList<>());
                    changed = true;
                }
            }
            if (changed) {
                Files.write(path, unit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to rewrite %s", path), err);
        }
    }

    private static String simpleName(final String type) {
        final int last = type.lastIndexOf('.');
        return last >= 0 ? type.substring(last + 1) : type;
    }
}
